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

package com.here.platform.example.location.scala.standalone

import java.nio.file.Files

import com.here.hrn.HRN
import com.here.platform.example.location.utils.Visualization._
import com.here.platform.example.location.utils.{FileNameHelper, Visualization}
import com.here.platform.location.core.geospatial.{GeoCoordinate, LineStringOperations, LineStrings}
import com.here.platform.location.core.graph.{PropertyMap, RangeBasedProperty}
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.{Forward, Vertex, Vertices}
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.integration.optimizedmap.roadattributes.FunctionalClass

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

  val catalogFactory = new StandaloneCatalogFactory()

  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)

    val proximitySearch = ProximitySearches.vertices(optimizedMap, cacheManager)

    val verticesInRange = proximitySearch.search(MesseNord, RadiusInMeters).map(_.element)

    println(s"Number of vertices in range: ${verticesInRange.size}")

    val roadAttributes = PropertyMaps.RoadAttributes(optimizedMap, cacheManager)

    val colorByFunctionalClass = Map(
      FunctionalClass.FC1 -> Red,
      FunctionalClass.FC2 -> Green,
      FunctionalClass.FC3 -> Blue,
      FunctionalClass.FC4 -> Yellow,
      FunctionalClass.FC5 -> Gray
    )

    val verticesWithProperties = for {
      vertex <- verticesInRange
      rangeBasedProperties = roadAttributes
        .functionalClass(vertex)
        .map(
          property =>
            RangeBasedProperty(property.start,
                               property.end,
                               (property.value, colorByFunctionalClass(property.value))))
    } yield VertexWithProperty(vertex, rangeBasedProperties)

    val geometryPropertyMap = PropertyMaps.geometry(optimizedMap, cacheManager)

    serializeToGeoJson(verticesWithProperties, geometryPropertyMap)
  } finally {
    catalogFactory.terminate()
  }

  private def serializeToGeoJson[LS: LineStringOperations](
      crossingSegments: Iterable[VertexWithProperty[(FunctionalClass, Color)]],
      geometry: PropertyMap[Vertex, LS]): Unit = {
    import au.id.jazzy.play.geojson._
    import com.here.platform.example.location.utils.Visualization._
    import com.here.platform.location.core.geospatial.GeoCoordinate._
    import play.api.libs.json._

    import scala.collection.immutable

    val segmentsAsFeatures = crossingSegments
      .flatMap {
        case VertexWithProperty(vertex, properties) =>
          properties.map {
            case RangeBasedProperty(start, end, (fc, color)) =>
              val partialLine = LineStrings.cut(geometry(vertex), Seq((start, end))).head
              val shift =
                Visualization.shiftNorthWest(if (Vertices.directionOf(vertex) == Forward) 2 else -2) _
              Feature(partialLine.copy(points = partialLine.points.map(shift)),
                      Some(Stroke(color) + ("FC" -> JsString(fc.toString))))
          }
      }
      .to[immutable.Seq]
    val json = Json.toJson(FeatureCollection(segmentsAsFeatures))
    val path = FileNameHelper.exampleJsonFileFor(this).toPath
    Files.write(path, Json.prettyPrint(json).getBytes)
    println("\nA GeoJson representation of the result is available in " + path + "\n")
  }
}
