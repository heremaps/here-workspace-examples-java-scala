/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package com.here.platform.data.processing.example.scala.validation

import com.here.olp.util.geo.{BoundingBox, GeoCoordinate}
import com.here.olp.util.quad.HereQuad
import com.here.platform.data.processing.validation.{AggregatedLongAccumulator, Feature, GeoJson}
import com.here.platform.data.processing.validation.scalatest.{Bindings, PayloadAndGeometry}
import com.here.schema.rib.v2.topology_geometry.{Node, Segment}
import com.here.schema.rib.v2.topology_geometry_partition.TopologyGeometryPartition
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Suite

import scala.collection.immutable

/** The data under test: a topology-geometry partitions and all topology-geometry partitions
  * referenced from its nodes and segments.
  *
  * @param tileId The tile ID.
  * @param partition The current partitions.
  * @param references The referenced partitions, indexed by their `partitionName` field.
  */
case class TopologyGeometryData(
    tileId: Long,
    partition: TopologyGeometryPartition,
    references: Map[String, TopologyGeometryPartition]
)

/** Suite that tests the validity of a topology-geometry partitions. This suite does not pretend to
  * be a complete validation suite, and it's for demonstration purpose only.
  *
  * For each partition under test we run a set of partition-level tests (toplevel), node-level tests
  * (nested) and segment-level tests (nested).
  */
class TopologyGeometrySuite extends AnyFlatSpec with Bindings[TopologyGeometryData] with Matchers {
  // Cache the bounding box of the current tile for later use in the test cases.
  private val bbox: BoundingBox = new HereQuad(data.tileId).getBoundingBox

  // Utility class used to enable O(1) access to some partition features (namely nodes and segments)
  // given their partition name and identifier.
  // The cache is populated lazily the first time a given topology-geometry partition (either the
  // one under test or one of its references) is accessed.
  private class LookupCache[T](
      extractObjects: TopologyGeometryPartition => Seq[T],
      extractIdentifier: T => String
  ) {
    private val cache = collection.mutable.Map.empty[String, Map[String, T]]
    private def createLookup(objs: Seq[T]): Map[String, T] =
      objs.groupBy(extractIdentifier).mapValues(_.head)

    def get(partitionName: String, identifier: String): Option[T] =
      cache
        .getOrElseUpdate(
          partitionName,
          if (partitionName == data.partition.partitionName) {
            createLookup(extractObjects(data.partition))
          } else {
            data.references
              .get(partitionName)
              .map(extractObjects)
              .map(createLookup)
              .getOrElse(Map.empty)
          }
        )
        .get(identifier)
  }

  // Lookup for O(1) access to segments.
  private val segmentLookup = new LookupCache[Segment](_.segment, _.identifier)

  // Lookup for O(1) access to nodes.
  private val nodeLookup = new LookupCache[Node](_.node, _.identifier)

  // Update some top-level accumulators. We keep track of the number of nodes and segments per tile,
  // using `AggregatedLongAccumulator` instances.

  context.withAccumulator[AggregatedLongAccumulator]("nodes-per-tile")(_ + data.partition.node.size)

  context.withAccumulator[AggregatedLongAccumulator]("segments-per-tile")(
    _ + data.partition.segment.size
  )

  // Partition-level test cases.

  "partitionName" should "be the tile ID" in {
    data.partition.partitionName.toLong shouldBe data.tileId
  }

  // Feature-level test cases. We use nested scalatest suites to test each node and segment instance
  // with specific sub-suites.

  private val nodeSpecs = data.partition.node.iterator.map(new NodeSpec(_))
  private val segmentSpecs = data.partition.segment.iterator.map(new SegmentSpec(_))

  override val nestedSuites: immutable.IndexedSeq[Suite] = (nodeSpecs ++ segmentSpecs).toIndexedSeq

  // Feature-level nested suites implementation.

  private class NodeSpec(node: Node) extends AnyFlatSpec with Matchers with PayloadAndGeometry {
    override val onFailPayload: Option[Any] = Some(node)
    override val onFailGeometry: Seq[Feature] = toGeoJson(node)
    override val onSkipPayload: Option[Any] = onFailPayload
    override val onSkipGeometry: Seq[Feature] = onFailGeometry

    context.withAccumulator[AggregatedLongAccumulator]("segments-per-node")(
      _ + node.segmentRef.size
    )

    "Node" should "be inside its host tile" in {
      val point = node.geometry.get
      bbox.contains(new GeoCoordinate(point.latitude, point.longitude)) shouldBe true
    }

    it should "not have zero segment references" in {
      node.segmentRef should not be empty
    }

    it should "not have dangling references" in {
      node.segmentRef.foreach { ref =>
        // In case of failure store the dangling reference in the report
        onFail(payload = Some(ref)) {
          segmentLookup.get(ref.partitionName, ref.identifier) shouldBe defined
        }
      }
    }
  }

  private class SegmentSpec(segment: Segment)
      extends AnyFlatSpec
      with Matchers
      with PayloadAndGeometry {
    override val onFailPayload: Option[Any] = Some(segment)
    override val onFailGeometry: Seq[Feature] = toGeoJson(segment)
    override val onSkipPayload: Option[Any] = onFailPayload
    override val onSkipGeometry: Seq[Feature] = onFailGeometry

    context.withAccumulator[AggregatedLongAccumulator]("segment-length")(_ + segment.length.toLong)

    "Segment" should "not have dangling references" in {
      val startNodeRef = segment.startNodeRef.get
      val endNodeRef = segment.endNodeRef.get
      nodeLookup.get(startNodeRef.partitionName, startNodeRef.identifier) shouldBe defined
      nodeLookup.get(endNodeRef.partitionName, endNodeRef.identifier) shouldBe defined
    }

    it should "start from its host tile" in {
      segment.startNodeRef.get.partitionName shouldBe data.tileId.toString
    }

    it should "start from its start-node geometry" in {
      val startNodeRef = segment.startNodeRef.get
      val startNodeGeometry =
        nodeLookup
          .get(startNodeRef.partitionName, startNodeRef.identifier)
          .getOrElse(cancel()) // dangling reference: we skip this test
          .geometry
          .get

      segment.geometry.flatMap(_.point.headOption).get shouldBe startNodeGeometry
    }

    it should "end in its end-node geometry" in {
      val endNodeRef = segment.endNodeRef.get
      val endNodeGeometry =
        nodeLookup
          .get(endNodeRef.partitionName, endNodeRef.identifier)
          .getOrElse(cancel()) // dangling reference: we skip this test
          .geometry
          .get

      segment.geometry.flatMap(_.point.lastOption).get shouldBe endNodeGeometry
    }
  }

  private def toGeoJson(node: Node): Seq[Feature] =
    node.geometry
      .map(g => GeoJson.makeFeature(GeoJson.makePoint(g.longitude, g.latitude)))
      .toSeq

  private def toGeoJson(segment: Segment): Seq[Feature] =
    segment.geometry
      .map(
        g =>
          GeoJson.makeFeature(
            GeoJson.makeLineString(g.point.map(p => GeoJson.makePoint(p.longitude, p.latitude)))
          )
      )
      .toSeq
}
