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

package com.here.platform.data.processing.example.scala.pedestrian.deltasets

import au.id.jazzy.play.geojson.{Feature, FeatureCollection, LineString, LngLat}
import com.here.platform.data.processing.blobstore.{Payload, Retriever}
import com.here.platform.data.processing.catalog.Partition._
import com.here.platform.data.processing.compiler.OutKey
import com.here.platform.data.processing.driver.Default
import com.here.platform.data.processing.driver.deltasets.Resolver
import com.here.platform.pipeline.logging.ContextLogging
import com.here.schema.rib.v2._
import com.here.schema.rib.v2.anchor.SegmentAnchor
import com.here.schema.rib.v2.road_attributes_partition.RoadAttributesPartition
import com.here.schema.rib.v2.topology_geometry.Segment
import com.here.schema.rib.v2.topology_geometry_partition.TopologyGeometryPartition
import play.api.libs.json.{JsObject, JsString, Json}

/** Encapsulates all code and data that may be distributed across the cluster. Everything in this
  * class must be serializable.
  *
  * @param retriever To retrieve payloads.
  */
class Compiler(config: CompilerConfig, retriever: Retriever)
    extends Serializable
    with ContextLogging {
  /** Given a tile and the metadata for the corresponding topology and road tiles (if any),
    * the list of segment features that are accessible by pedestrians.
    *
    * @return List of segment features that are accessible by pedestrians.
    */
  def extractPedestrianFeatures(resolver: Resolver,
                                roadKey: Key,
                                roadMeta: Meta): List[Feature[LngLat]] = {
    // Read road partition
    val roadTile =
      RoadAttributesPartition.parseFrom(retriever.getPayload(roadKey, roadMeta).content)
    val segmentAnchors = getPedestrianSegmentAnchors(roadTile)

    if (segmentAnchors.nonEmpty) {
      val topologyKey = Key(Defs.In.RibCatalog, Defs.In.TopologyGeometryLayer, roadKey.partition)
      val topologyMeta = resolver.resolve(topologyKey).get
      val topologyTile =
        TopologyGeometryPartition.parseFrom(retriever.getPayload(topologyKey, topologyMeta).content)

      // Load all topologies
      val polyLines: Map[String, Segment] =
        topologyTile.segment.map(segment => segment.identifier -> segment)(collection.breakOut)

      // Get pedestrian intermediate data
      segmentAnchors.flatMap(segmentAnchor =>
        segmentAnchor.orientedSegmentRef.map(getModelPolyline(_, polyLines)))
    } else {
      Nil
    }
  }

  /** Get the output key of an intermediate data object.
    *
    * The intermediate object is assigned to the output layer partition containing its first point.
    * The level of the outut partition is set in the compiler configuration.
    *
    * @param intermediateData The intermediate data object
    * @return The output partition key.
    */
  def getOutputKey(intermediateData: Feature[LngLat]): OutKey = {
    val point = intermediateData.geometry.asInstanceOf[LineString[LngLat]].coordinates.head
    val latitude = point.lat
    val longitude = point.lng

    OutKey(Default.OutCatalogId,
           Defs.Out.PedestrianSegments,
           HereTile(new com.here.olp.util.quad.HereQuad(latitude, longitude, config.outputLevel)))
  }

  /** Given a partition of the output layer and all the related topologies builds the output
    * partition.
    *
    * @param outKey       Partition of the output layer.
    * @param intermediate The topologies related to the output partition key.
    * @return the optional payload containing the results, if any.
    */
  def toJSON(outKey: OutKey, intermediate: Iterable[Feature[LngLat]]): Option[Payload] =
    if (intermediate.nonEmpty) {
      // Stabilize based on id's and filter partition content
      val data = intermediate.toList.distinct.sortBy(_.id.get.toString)

      // Build the output partition protocol buffer and add the poly-lines
      val payload = Json.toJson(FeatureCollection[LngLat](data)).toString

      // Log results
      logger.info(s"Publishing ${intermediate.size} features to $outKey")

      // Return the result
      Some(Payload(payload.getBytes))
    } else {
      None
    }

  /** Gets the compiler model Feature.
    *
    * @param reference The RIB reference.
    * @param segments The map of segments to lookup.
    * @return The compiler model feature.
    */
  private def getModelPolyline(reference: anchor.SegmentAnchor.OrientedSegmentReference,
                               segments: Map[String, topology_geometry.Segment]) = {
    val identifier = reference.getSegmentRef.identifier
    val segment = segments.get(identifier)

    require(segment.nonEmpty, "Topology not found for the identifier: " + identifier)

    val points = segment
      .flatMap(
        _.geometry.map(_.point.map(point => LngLat(point.longitude, point.latitude))(
          collection.breakOut): List[LngLat]))
      .getOrElse(throw new NoSuchElementException(
        "Could not find required data in segment for topology with the identifier: " + identifier))

    Feature(LineString(points), Some(JsObject.empty), Some(JsString(identifier)))
  }

  /** Gets segment anchors of pedestrian only access.
    *
    * @param roadPartition The decoded road partition.
    * @return A stream of pedestrian segment anchors.
    */
  private def getPedestrianSegmentAnchors(
      roadPartition: road_attributes_partition.RoadAttributesPartition): List[SegmentAnchor] =
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
      .map(roadPartition.segmentAnchor)(collection.breakOut)
}
