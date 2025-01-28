/*
 * Copyright (C) 2017-2025 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.platform.data.processing.example.scala.pedestrian.protobuf

import com.here.olp.util.quad.HereQuad
import com.here.platform.data.processing.blobstore.Payload
import com.here.platform.data.processing.catalog.Partition.HereTile
import com.here.platform.data.processing.compiler._
import com.here.platform.data.processing.compiler.reftree.RefTree.RefName
import com.here.platform.data.processing.compiler.reftree.{
  CompileInFnWithRefs,
  Ref,
  RefTree,
  Subject
}
import com.here.platform.data.processing.driver.{Default, DriverContext}
import com.here.platform.data.processing.example.scala.pedestrian.protobuf.Defs.{
  In,
  LayerDefs,
  Out,
  Refs
}
import com.here.platform.pipeline.logging.ContextLogging
import com.here.platform.data.processing.spark.partitioner.Partitioner
import com.here.platform.schema.data.processing.example.scala.pedestrian.protobuf.model.v2.model.{
  Geometry,
  Polyline
}
import com.here.schema.geometry.v2.geometry
import com.here.schema.rib.v2.anchor.SegmentAnchor
import com.here.schema.rib.v2._

/**
  * @param ctx A gate for user to the facilities provided by the framework.
  * @param cfg the compiler specific configuration
  */
class Compiler(ctx: DriverContext, cfg: CompilerConfig)
    extends RefTreeCompiler[Polyline]
    with CompileInFnWithRefs[Polyline]
    with CompileOut1To1Fn[Polyline]
    with LayerDefs
    with ContextLogging {
  /**
    * retriever to get input data
    */
  val retriever = ctx.inRetriever(In.RibCatalog)

  /**
    * Reference structure. We define RoadLayer as subject layer and Topology as reference of RoadLayer. So
    * schematically it would look like RoadLayer -> Topology. In other words our output tile with geometry depends on
    * RoadLayer which in its turn depends on Topology. Knowing this dependency model, incremental compiler will
    * recompile geometry tile once either RoadLayer or Topology that it depends on change.
    */
  override def refStructure = RefTree(
    Subject(layer = (In.RibCatalog, In.RoadLayer),
            refs = Ref(refName = Refs.TopologyRef, layer = (In.RibCatalog, In.TopologyLayer)))
  )

  /**
    * Given RoadLayer partition, returns all partitions that are referenced by it
    *
    * @param src        partition of the RoadLayer
    * @return All partitions that are referenced by RoadLayer
    */
  override def resolveFn(src: (InKey, InMeta)): Map[RefName, Set[InKey]] = {
    // Read subject partition
    val (key, meta) = src
    val roadPartition =
      road_attributes_partition.RoadAttributesPartition
        .parseFrom(retriever.getPayload(key, meta).content)

    // Get segment anchors
    val segmentAnchors = getPedestrianSegmentAnchors(roadPartition)

    // Get the oriented references
    val orientedSegmentReferences = segmentAnchors.flatMap(_.orientedSegmentRef)

    // Get the referred topology partitions
    val topologyKeys: Set[InKey] = orientedSegmentReferences
      .map(orientedReference => getTopologyKey(orientedReference.getSegmentRef))(
        collection.breakOut)

    Map(Refs.TopologyRef -> topologyKeys)
  }

  /**
    * Get the topology partition key based from a reference
    *
    * @param reference the reference object
    * @return the referenced topology partition key
    */
  private def getTopologyKey(reference: common.Reference) =
    InKey(In.RibCatalog, In.TopologyLayer, HereTile(reference.partitionName.toLong))

  /**
    * Gets segment anchors of pedestrian access
    *
    * @param roadPartition the decoded road partition
    * @return a collection of pedestrian segment anchors
    */
  private def getPedestrianSegmentAnchors(
      roadPartition: road_attributes_partition.RoadAttributesPartition): Set[SegmentAnchor] =
    roadPartition.accessibleBy
      .filter(
        access =>
          access.appliesTo.exists(
            appliesTo =>
              appliesTo.pedestrians
                && !appliesTo.automobiles
                && !appliesTo.buses
                && !appliesTo.carpools
                && !appliesTo.deliveries
                && !appliesTo.emergencyVehicles
                && !appliesTo.motorcycles
                && !appliesTo.taxis
                && !appliesTo.throughTraffic
                && !appliesTo.trucks))
      .flatMap(pedestrian => pedestrian.segmentAnchorIndex)
      .map(roadPartition.segmentAnchor(_))(collection.breakOut)

  /**
    * Given a RoadLayer partition and its references in TopologyLayer returns output key
    * and the intermediate data required to compile it.
    *
    * @param roadMeta   Partition on the RoadLayer
    * @param refs       TopologyLayer partition references.
    * @return output key for geometry and intermediate data required to compile it
    */
  override def compileInFn(roadMeta: (InKey, InMeta),
                           refs: Map[InKey, InMeta]): Iterable[(OutKey, Polyline)] = {
    logger.info(
      "processing references: " + refs.map { case (key, _) => key.toString }.mkString(", "))

    if (refs.isEmpty)
      Iterable.empty
    else {
      // Get intermediate data map
      val polyLines = getSegmentMap(refs)

      // Read subject partition
      val (key, meta) = roadMeta
      val roadPartition =
        road_attributes_partition.RoadAttributesPartition
          .parseFrom(retriever.getPayload(key, meta).content)

      // Get pedestrian intermediate data
      val intermediateData = getPedestrianSegmentAnchors(roadPartition)
        .flatMap(segmentAnchor =>
          segmentAnchor.orientedSegmentRef.map(getModelPolyline(_, polyLines)))

      intermediateData.map { intermediateData =>
        (getOutputKey(intermediateData), intermediateData)
      }
    }
  }

  /**
    * Gets a map segmentId -> segments for segments fast look-up
    *
    * @param topologyPartitions the topology partitions to decode
    * @return returns a segmentId -> polyline
    */
  private def getSegmentMap(
      topologyPartitions: Map[InKey, InMeta]): Map[String, topology_geometry.Segment] =
    // Get topologies map
    topologyPartitions
      .flatMap {
        case (key: InKey, meta: InMeta) =>
          topology_geometry_partition.TopologyGeometryPartition
            .parseFrom(retriever.getPayload(key, meta).content)
            .segment
      }
      .map(segment => segment.identifier -> segment)(collection.breakOut)

  /**
    * Gets the compiler model polyline
    *
    * @param reference the RIB reference
    * @param polyLines the map of polilines to look-up
    * @return the compiler model polyline
    */
  private def getModelPolyline(reference: anchor.SegmentAnchor.OrientedSegmentReference,
                               polyLines: Map[String, topology_geometry.Segment]) = {
    val identifier = reference.getSegmentRef.identifier
    val segment = polyLines.get(identifier)

    require(segment.nonEmpty, "Topology not found for the identifier: " + identifier)

    val points = geometry.LineString(segment
      .flatMap(_.geometry.map(_.point.map(point =>
        geometry.Point(point.latitude, point.longitude))))
      .getOrElse(throw new NoSuchElementException(
        "Could not find required data in segment for topology with the identifier: " + identifier)))

    Polyline(identifier, Some(points))
  }

  /**
    * Get the output key of an intermediate data object
    *
    * The intermediate object is assigned to the output layer partition containing its first point.
    * The level of the outut partition is set in the compiler configuration.
    *
    * @param intermediateData the intermediate data object
    * @return the output partition key
    */
  private def getOutputKey(intermediateData: Polyline) = {
    val latitude = intermediateData.getLineString.point.head.latitude
    val longitude = intermediateData.getLineString.point.head.longitude

    OutKey(Default.OutCatalogId,
           Out.RoadGeometryLayer,
           HereTile(new HereQuad(latitude, longitude, cfg.outputLevel)))
  }

  /**
    * Given a partition of the output layer and all the related topologies builds the output partition.
    *
    * @param outKey       Partition of the output layer
    * @param intermediate The topologies related to the output partition key.
    * @return the optional payload containing the results, if any.
    */
  override def compileOutFn(outKey: OutKey, intermediate: Iterable[Polyline]): Option[Payload] = {
    require(intermediate.iterator.hasNext, "Intermediate data cannot be empty in this compiler!")

    // Stabilize based on id's and filter partition content
    val data = intermediate.toSeq.distinct.sortBy(_.identifier)

    // Build the output partition protocol buffer and add the polylines
    val partition = Geometry(data)

    // Log results
    logger.info("processed data: " + partition.toString)

    // Return the result
    Some(Payload(partition.toByteArray))
  }

  /**
    * The output partitioner
    *
    * @param parallelism the number of partitioner
    * @return the same partitioner as the input
    */
  override def outPartitioner(parallelism: Int): Option[Partitioner[OutKey]] =
    inPartitioner(parallelism)

  /**
    * The input partitioner
    *
    * @param parallelism the number of partitioner
    * @return the hash partitioner
    */
  override def inPartitioner(parallelism: Int): Option[Partitioner[InKey]] =
    None // will use default partitioner
}
