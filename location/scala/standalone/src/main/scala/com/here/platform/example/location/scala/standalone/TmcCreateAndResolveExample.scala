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

import au.id.jazzy.play.geojson
import com.here.hrn.HRN
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.example.location.utils.Visualization.{Blue, Stroke, Yellow, _}
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.core.graph.PropertyMap
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.referencing._
import com.here.platform.location.tpeg2.XmlMarshallers
import com.here.platform.location.tpeg2.lrc.LocationReferencingContainer
import com.here.platform.location.tpeg2.tmc.TMCLocationReference
import play.api.libs.json.{JsString, Json}

import scala.collection.immutable

/** Create and resolve a TMC reference.
  *
  * The example searches for a well-known vertex that is covered by TMC,
  * and uses that to create a TMC reference. The reference is later resolved
  * and outputted for comparison.
  */
object TmcCreateAndResolveExample extends App {
  val coordinateInFriedenstrasse = GeoCoordinate(52.527111, 13.427079)

  val cacheManager = CacheManager.withLruCache()
  val catalogFactory = new StandaloneCatalogFactory()
  try {
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705)

    // Define a location that is covered by TMC
    val locationInFriedenstrasse = {
      val vertexInFriedenstrasse = ProximitySearches
        .vertices(optimizedMap, cacheManager)
        .search(coordinateInFriedenstrasse, 10)
        .head
        .element

      LinearLocation(Seq(vertexInFriedenstrasse))
    }

    // Create a reference for that location
    val tmcRefCreator =
      LocationReferenceCreators.tmc(optimizedMap, cacheManager)

    val tmcRef = tmcRefCreator.create(locationInFriedenstrasse)

    // Resolve the newly created reference
    val tmcRefResolver =
      LocationReferenceResolvers.tmc(optimizedMap, cacheManager)

    val resolvedLocation =
      tmcRefResolver.resolve(tmcRef)

    // Visualize the original location, the reference created, and the resolved location
    visualizeResults(optimizedMap, locationInFriedenstrasse, tmcRef, resolvedLocation.location)
  } finally catalogFactory.terminate()

  private def visualizeResults(optimizedMap: Catalog,
                               inputLocation: LinearLocation,
                               tmcRef: TMCLocationReference,
                               resolvedLocation: LinearLocation): Unit = {
    println(s"""Input location: $inputLocation
               |Resolved location: $resolvedLocation
               |Location reference:""".stripMargin)

    XmlMarshallers.locationReferencingContainer
      .marshall(LocationReferencingContainer(Seq(tmcRef)), Console.out)

    val geometries =
      PropertyMaps.geometry(optimizedMap, cacheManager)

    val searchPointFeatures = Seq(
      geojson.Feature(geojson.Point(coordinateInFriedenstrasse), Some(Stroke(Yellow))))
    val inputLocationFeatures = locationFeatures(geometries, inputLocation, Green, -5)
    val resolvedLocationFeatures = locationFeatures(geometries, resolvedLocation, Blue, 5)

    val allFeatures =
      (searchPointFeatures ++ inputLocationFeatures ++ resolvedLocationFeatures)
        .to[immutable.Seq]

    val json = Json.toJson(geojson.FeatureCollection(allFeatures))
    val path = FileNameHelper.exampleJsonFileFor(TmcCreateAndResolveExample).toPath
    Files.write(path, Json.prettyPrint(json).getBytes)
    println(s"""
               |A GeoJson representation of the resolved vertices is available in $path
               |""".stripMargin)
  }

  private def locationFeatures(geometries: PropertyMap[Vertex, PackedLineString],
                               location: LinearLocation,
                               color: Color,
                               shift: Double) =
    location.path.map { vertex =>
      val properties = Stroke(color) +
        ("vertex" -> JsString(s"${vertex.tileId.value}:${vertex.index.value}"))
      val vertexLineString = geometries(vertex)
      geojson.Feature(vertexLineString.copy(
                        coordinates = vertexLineString.coordinates.map(shiftNorthWest(shift))),
                      Some(properties))
    }
}
