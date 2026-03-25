/*
 * Copyright (C) 2017-2026 HERE Europe B.V.
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

package com.here.platform.example.location.scala.standalone

import com.here.platform.data.client.base.scaladsl.BaseClient
import com.here.platform.data.client.v2.api.scaladsl.versioned.VersionedHereTileLayerReader
import com.here.platform.data.client.v2.caching.scaladsl.versioned.{
  VersionedLayerReaderConfiguration,
  VersionedLayerReadersConfiguration
}
import com.here.platform.data.client.v2.main.scaladsl.DataClient
import com.here.platform.example.location.scala.standalone.utils.FileNameHelper
import com.here.platform.location.compilation.heremapcontent.AttributeAccessors
import com.here.platform.location.core.geospatial.Implicits._
import com.here.platform.location.core.geospatial._
import com.here.platform.location.core.graph.{
  PropertyMap,
  RangeBasedProperty,
  RangeBasedPropertyMap
}
import com.here.platform.location.inmemory.graph._
import com.here.platform.location.integration.heremapcontent.HereMapContent
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.geospatial.{
  HereMapContentReference,
  ProximitySearches,
  SegmentId
}
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}
import com.here.schema.rib.v2.anchor.SegmentAnchor
import com.here.schema.rib.v2.common.RelativeDirection
import com.here.schema.rib.v2.common_attributes.SpeedLimitAttribute
import com.here.schema.rib.v2.topology_attributes_partition.TopologyAttributesPartition

import java.io.FileOutputStream
import scala.util.Using

object RangeBasedPropertyConverter {
  def convert[T](
      it: Iterable[(SegmentAnchor, T)]): Map[(SegmentId, Direction), Seq[RangeBasedProperty[T]]] =
    it.flatMap {
        case (anchor, attribute) if anchor.orientedSegmentRef.length == 1 =>
          val orientedSegmentRef = anchor.orientedSegmentRef.head

          val forwardEntry =
            Option.when(isForward(anchor.attributeOrientation, orientedSegmentRef)) {
              toDirectedSegmentId(orientedSegmentRef, Forward) -> RangeBasedProperty(
                anchor.firstSegmentStartOffset.getOrElse(0.0),
                anchor.lastSegmentEndOffset.getOrElse(1.0),
                attribute
              )
            }
          val backwardEntry =
            Option.when(isBackward(anchor.attributeOrientation, orientedSegmentRef)) {
              toDirectedSegmentId(orientedSegmentRef, Backward) -> RangeBasedProperty(
                1.0 - anchor.lastSegmentEndOffset.getOrElse(1.0),
                1.0 - anchor.firstSegmentStartOffset.getOrElse(0.0),
                attribute
              )
            }
          Seq(forwardEntry, backwardEntry).flatten
        case _ => Seq.empty
      }
      .groupBy { case (directedSegmentId, _) => directedSegmentId }
      .map {
        case (directedSegmentId, properties) =>
          (directedSegmentId, properties.map { case (_, p) => p }.toSeq)
      }

  private def isForward(attributeOrientation: RelativeDirection,
                        orientedSegmentRef: SegmentAnchor.OrientedSegmentReference) =
    (attributeOrientation.isBoth
      || (attributeOrientation.isForward && !orientedSegmentRef.inverted)
      || (attributeOrientation.isBackward && orientedSegmentRef.inverted))

  private def isBackward(attributeOrientation: RelativeDirection,
                         orientedSegmentRef: SegmentAnchor.OrientedSegmentReference) =
    (attributeOrientation.isBoth
      || (attributeOrientation.isForward && orientedSegmentRef.inverted)
      || (attributeOrientation.isBackward && !orientedSegmentRef.inverted))

  private def toDirectedSegmentId(orientedSegmentRef: SegmentAnchor.OrientedSegmentReference,
                                  direction: Direction) =
    (SegmentId(orientedSegmentRef.segmentRef.get.identifier), direction)
}

// You can add here the other fields that need to be compiled
case class OnTheFlyPartition(
    speedLimitWithColor: Map[(SegmentId, Direction), Seq[RangeBasedProperty[(Int, Color)]]])

object OnTheFlyPartition {
  private val speedLimitWithColorAccessor =
    AttributeAccessors
      .forHereMapContentSegmentAnchor[TopologyAttributesPartition, SpeedLimitAttribute, Int](
        _.speedLimit,
        _.value
      )
      .map(s => (s, Color.hsb(Math.min(Math.max(s.toDouble, 0), 60), 1, 1)))

  def fromHereMapContent(partition: TopologyAttributesPartition): OnTheFlyPartition =
    OnTheFlyPartition(RangeBasedPropertyConverter.convert(speedLimitWithColorAccessor(partition)))
}

class OnTheFlyRangeBasedPropertyMap[P, T](
    reader: VersionedHereTileLayerReader[P],
    version: Long,
    vertexToHereMapContentReference: PropertyMap[Vertex, HereMapContentReference],
    accessor: P => Map[(SegmentId, Direction), Seq[RangeBasedProperty[T]]]
) extends RangeBasedPropertyMap[Vertex, T] {
  override def apply(key: Vertex): Seq[RangeBasedProperty[T]] = {
    val hmcRef = vertexToHereMapContentReference(key)
    reader
      .read(version, hmcRef.partitionId.value.toLong)
      .flatMap { p =>
        accessor(p).get((hmcRef.segmentId, hmcRef.direction))
      }
      .getOrElse(Seq.empty)
  }

  override def contains(key: Vertex): Boolean = vertexToHereMapContentReference.contains(key)
}

/** An example that shows how to compile Topology attributes from HERE Map Content on the fly
  * and use them as properties of vertices from the `Optimized Map for Location Library`.
  */
object OnTheFlyCompiledPropertyMapExample extends App {
  case class VertexWithProperty[T](vertex: Vertex, rangeBasedProperties: Seq[RangeBasedProperty[T]])

  val om4llVersion = 8181L
  val brandenburgerTor = GeoCoordinate(52.516268, 13.377700)
  val radiusInMeters = 1000.0

  val baseClient = BaseClient()

  try {
    val optimizedMapCatalog = OptimizedMapCatalog
      .from(OptimizedMap.v2.HRN)
      .usingBaseClient(baseClient)
      .newInstance

    val optimizedMap = optimizedMapCatalog
      .version(om4llVersion)

    val hmcVersion = optimizedMapCatalog.versionInfo
      .resolveDependencyVersion(om4llVersion, HereMapContent.v2.HRN)
      .get

    val hmcCatalog = DataClient(baseClient)
      .catalogBuilder(HereMapContent.v2.HRN)
      .withVersionedLayerReadersConfiguration(
        VersionedLayerReadersConfiguration().withLayer(
          "topology-attributes",
          VersionedLayerReaderConfiguration(is =>
            OnTheFlyPartition.fromHereMapContent(TopologyAttributesPartition.parseFrom(is)))
        ))
      .build

    val vertexToHereMapContentReference = PropertyMaps(optimizedMap).vertexToHereMapContentReference

    val speedLimitWithColor = new OnTheFlyRangeBasedPropertyMap(
      hmcCatalog.versionedLayers
        .versionedHereTileLayer("topology-attributes")
        .reader[OnTheFlyPartition],
      hmcVersion,
      vertexToHereMapContentReference,
      (p: OnTheFlyPartition) => p.speedLimitWithColor)

    val proximitySearch = ProximitySearches(optimizedMap).vertices

    val verticesInRange = proximitySearch.search(brandenburgerTor, radiusInMeters).map(_.element)

    println(s"Number of vertices in range: ${verticesInRange.size}")

    val verticesWithProperties: Iterable[VertexWithProperty[(Int, Color)]] = for {
      vertex <- verticesInRange
      rangeBasedProperties = speedLimitWithColor(vertex)
    } yield VertexWithProperty(vertex, rangeBasedProperties)

    val geometryPropertyMap = PropertyMaps(optimizedMap).geometry

    serializeToGeoJson(verticesWithProperties, geometryPropertyMap)
  } finally {
    baseClient.shutdown()
  }

  private def serializeToGeoJson[LS: LineStringOperations](
      crossingSegments: Iterable[VertexWithProperty[(Int, Color)]],
      geometry: PropertyMap[Vertex, LS]): Unit = {
    val segmentsAsFeatures = crossingSegments
      .flatMap {
        case VertexWithProperty(vertex, properties) =>
          properties.map {
            case RangeBasedProperty(start, end, (speedLimit, color)) =>
              val partialLine = LineStrings.cut(geometry(vertex), Seq((start, end))).head
              val shiftedPartialLine =
                shiftNorthWest(partialLine, if (Vertices.directionOf(vertex) == Forward) 2 else -2)
              Feature.lineString(
                shiftedPartialLine,
                SimpleStyleProperties().stroke(color).add("speedLimit", speedLimit.toString))
          }
      }
    val path = FileNameHelper.exampleJsonFileFor(this)
    Using.resource(new FileOutputStream(path)) { fos =>
      FeatureCollection(segmentsAsFeatures).writePretty(fos)
      println("\nA GeoJson representation of the result is available in " + path + "\n")
    }
  }

  private def shiftNorthWest[LS: LineStringOperations](
      ls: LS,
      distance: Double): LineString[GeoCoordinate] = {
    val projection = SinusoidalProjection
    LineString(ls.points.map { pgc =>
      val projected = projection.to(pgc, pgc)
      projection.from(pgc, projected.copy(x = projected.x + distance, y = projected.y + distance))
    })
  }
}
