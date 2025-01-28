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

package com.here.platform.example.location.scala.standalone

import java.io.FileOutputStream

import com.here.platform.data.client.base.scaladsl.BaseClient
import com.here.platform.example.location.scala.standalone.utils.FileNameHelper
import com.here.platform.location.core.geospatial.Implicits._
import com.here.platform.location.core.geospatial._
import com.here.platform.location.core.graph.{PropertyMap, RangeBasedProperty}
import com.here.platform.location.inmemory.graph.{Forward, Vertex, Vertices}
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.{OptimizedMap, OptimizedMapLayers}
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.integration.optimizedmap.roadattributes.FunctionalClass
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}

/** An example that shows how to compile Road attributes from HERE Map Content on the fly
  * and use them as properties of vertices from the `Optimized Map for Location Library`.
  */
object FunctionalClassExample extends App {
  case class VertexWithProperty[T](vertex: Vertex, rangeBasedProperties: Seq[RangeBasedProperty[T]])

  val MesseNord = GeoCoordinate(
    52.506671,
    13.282895
  )
  val RadiusInMeters = 1000.0

  val baseClient = BaseClient()

  try {
    val optimizedMap: OptimizedMapLayers =
      OptimizedMapCatalog
        .from(OptimizedMap.v2.HRN)
        .usingBaseClient(baseClient)
        .newInstance
        .version(1293L)

    val proximitySearch = ProximitySearches(optimizedMap).vertices

    val verticesInRange = proximitySearch.search(MesseNord, RadiusInMeters).map(_.element)

    println(s"Number of vertices in range: ${verticesInRange.size}")

    val roadAttributes = PropertyMaps(optimizedMap).roadAttributes

    val Red = Color("#e87676")
    val Green = Color("#58db58")
    val Blue = Color("#76bde8")
    val Yellow = Color("#f7f431")
    val Gray = Color("#777777")

    val colorByFunctionalClass = Map(
      FunctionalClass.FC1 -> Red,
      FunctionalClass.FC2 -> Green,
      FunctionalClass.FC3 -> Blue,
      FunctionalClass.FC4 -> Yellow,
      FunctionalClass.FC5 -> Gray
    )

    val verticesWithProperties: Iterable[VertexWithProperty[(FunctionalClass, Color)]] = for {
      vertex <- verticesInRange
      rangeBasedProperties = roadAttributes
        .functionalClass(vertex)
        .map(
          property =>
            RangeBasedProperty(property.start,
                               property.end,
                               (property.value, colorByFunctionalClass(property.value))))
    } yield VertexWithProperty(vertex, rangeBasedProperties)

    val geometryPropertyMap = PropertyMaps(optimizedMap).geometry

    serializeToGeoJson(verticesWithProperties, geometryPropertyMap)
  } finally {
    baseClient.shutdown()
  }

  private def serializeToGeoJson[LS: LineStringOperations](
      crossingSegments: Iterable[VertexWithProperty[(FunctionalClass, Color)]],
      geometry: PropertyMap[Vertex, LS]): Unit = {
    val segmentsAsFeatures = crossingSegments
      .flatMap {
        case VertexWithProperty(vertex, properties) =>
          properties.map {
            case RangeBasedProperty(start, end, (fc, color)) =>
              val partialLine = LineStrings.cut(geometry(vertex), Seq((start, end))).head
              val shiftedPartialLine =
                shiftNorthWest(partialLine, if (Vertices.directionOf(vertex) == Forward) 2 else -2)
              Feature.lineString(shiftedPartialLine,
                                 SimpleStyleProperties().stroke(color).add("FC" -> fc.toString))
          }
      }
    val path = FileNameHelper.exampleJsonFileFor(this)
    val fos = new FileOutputStream(path)
    FeatureCollection(segmentsAsFeatures).writePretty(fos)
    fos.close()
    println("\nA GeoJson representation of the result is available in " + path + "\n")
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
