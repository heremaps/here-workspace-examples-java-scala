/*
 * Copyright (C) 2017-2022 HERE Europe B.V.
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

package com.here.platform.data.processing.example.scala.geometry.lifter

import com.here.olp.util.quad.HereQuad
import com.here.platform.data.processing.example.scala.geometry.lifter.Defs._
import com.here.platform.data.processing.blobstore.{Payload, Retriever}
import com.here.platform.data.processing.catalog.Partition.{Key, Meta}
import com.here.platform.data.processing.catalog.Partition.HereTile
import com.here.platform.data.processing.catalog.Implicits._
import com.here.platform.data.processing.compiler._
import com.here.platform.data.processing.driver.{Default, DriverContext}
import com.here.platform.pipeline.logging.ContextLogging
import com.here.platform.data.processing.spark.partitioner.{LocalityAwarePartitioner, Partitioner}
import com.here.schema.rib.v2.topology_geometry.{Node, Segment}
import com.here.schema.rib.v2.common.Reference
import com.here.schema.rib.v2.topology_geometry_partition.TopologyGeometryPartition

class Compiler(context: DriverContext, compilerConf: CompilerConfig)
    extends DirectMToNCompiler[(Key, Meta)]
    with CompileOut1To1Fn[(Key, Meta)]
    with ContextLogging
    with LayerDefinitions {
  // retriever from input catalog
  private val retriever: Retriever = context.inRetriever(In.RibCatalog)

  /** The partitioner for the input data
    *
    * @param parallelism The parallelism of the partitioner
    * @return The input optional partitioner with the given parallelism
    *
    */
  override def inPartitioner(parallelism: Int): Option[Partitioner[InKey]] =
    Some(LocalityAwarePartitioner(parallelism, compilerConf.partitionerLevel))

  /** The partitioner for the output data
    *
    * @param parallelism The parallelism of the partitioner
    * @return The output optional partitioner with the given parallelism
    *
    */
  override def outPartitioner(parallelism: Int): Option[Partitioner[OutKey]] =
    inPartitioner(parallelism)

  /**
    * Calculates which output partitions the given input partition impacts.
    *
    * @param inKey      The input partition being mapped
    * @return The output partitions to which the given input partition maps
    */
  override def mappingFn(inKey: InKey): Iterable[OutKey] = {
    val tile = getAncestorTile(inKey.partition.hereTileId)
    Iterable(OutKey(Default.OutCatalogId, Defs.Out.LayerId, tile))
  }

  /**
    * Given a TopologyLayer partition returns output key
    * and the intermediate data required to compile it.
    *
    * @param topologyMeta Partition on the RoadLayer
    * @return output key for geometry and intermediate data required to compile it
    */
  override def compileInFn(topologyMeta: (InKey, InMeta)): (Key, Meta) =
    topologyMeta

  /**
    * Given a partition of the output layer and all the related topologies builds the output partition.
    *
    * @param outKey       Partition of the output layer
    * @param intermediate The topologies related to the output partition key.
    * @return the optional payload containing the results, if any.
    */
  override def compileOutFn(outKey: OutKey,
                            intermediate: Iterable[(Key, Meta)]): Option[Payload] = {
    val inTopologies: IndexedSeq[TopologyGeometryPartition] = intermediate.map {
      case (key, meta) =>
        TopologyGeometryPartition.parseFrom(retriever.getPayload(key, meta).content)
    }(collection.breakOut)

    val outTopology =
      LevelLifter.liftTopology(outKey.partition.hereTileId, inTopologies.sortBy(_.partitionName))

    Some(Payload(outTopology.toByteArray))
  }

  object LevelLifter {
    /** Merge a group of lower level tiles into a single tile
      *
      * @param topologies a sequence of lower level topology partitions
      * @return a partition combining input topologies with adjusted references
      */
    private[lifter] def liftTopology(
        tileId: Long,
        topologies: Seq[TopologyGeometryPartition]): TopologyGeometryPartition = {
      def liftReference(reference: Reference): Reference =
        reference.copy(partitionName = getAncestorTile(reference.partitionName.toLong).toString)

      def liftNode(node: Node) =
        node.copy(segmentRef = node.segmentRef.map(liftReference))

      def liftSegment(seqment: Segment) =
        seqment.copy(startNodeRef = seqment.startNodeRef.map(liftReference),
                     endNodeRef = seqment.endNodeRef.map(liftReference))

      // collect nodes and update partition name for segmentRef
      val nodes: Seq[Node] = topologies.flatMap(partition => partition.node.map(liftNode))

      // collect segments and update partition name for startNodeRef and endNodeRef
      val segments: Seq[Segment] =
        topologies.flatMap(partition => partition.segment.map(liftSegment))

      TopologyGeometryPartition(
        partitionName = tileId.toString,
        node = nodes,
        segment = segments
      )
    }
  }

  /**
    * Get ancestor tile at the configured zoom level
    *
    * @param key quad key of current tile
    * @return tile at the configured zoom level
    */
  private def getAncestorTile(key: Long) =
    HereTile(new HereQuad(key).getAncestor(compilerConf.outputLevel))
}
