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

package com.here.platform.example.location.scala.standalone

import com.here.platform.data.client.base.scaladsl.BaseClient
import com.here.platform.example.location.scala.standalone.utils.FileNameHelper

import java.io.FileOutputStream
import com.here.platform.location.core.geospatial.Implicits._
import com.here.platform.location.core.geospatial.{
  GeoCoordinate,
  LineString,
  LineStringOperations,
  SinusoidalProjection
}
import com.here.platform.location.core.graph.PropertyMap
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.{OptimizedMap, OptimizedMapLayers}
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}
import com.here.platform.location.referencing._
import com.here.platform.location.tpeg2.XmlMarshallers
import com.here.platform.location.tpeg2.lrc.LocationReferencingContainer
import com.here.platform.location.tpeg2.tmc.TMCLocationReference

/** Create and resolve a TMC reference.
  *
  * The example searches for a well-known vertex that is covered by TMC,
  * and uses that to create a TMC reference. The reference is later resolved
  * and outputted for comparison.
  */
object TmcCreateAndResolveExample extends App {
  val coordinateInFriedenstrasse = GeoCoordinate(52.527111, 13.427079)

  val baseClient = BaseClient()
  try {
    val optimizedMap: OptimizedMapLayers =
      OptimizedMapCatalog
        .from(OptimizedMap.v2.HRN)
        .usingBaseClient(baseClient)
        .newInstance
        .version(1293L)

    // Define a location that is covered by TMC
    val locationInFriedenstrasse = {
      val vertexInFriedenstrasse = ProximitySearches(optimizedMap).vertices
        .search(coordinateInFriedenstrasse, 10)
        .head
        .element

      LinearLocation(Seq(vertexInFriedenstrasse))
    }

    // Create a reference for that location
    val tmcRefCreator =
      LocationReferenceCreators(optimizedMap).tmc

    val tmcRef = tmcRefCreator.create(locationInFriedenstrasse)

    // Resolve the newly created reference
    val tmcRefResolver =
      LocationReferenceResolvers(optimizedMap).tmc

    val resolvedLocation =
      tmcRefResolver.resolve(tmcRef)

    // Visualize the original location, the reference created, and the resolved location
    visualizeResults(optimizedMap, locationInFriedenstrasse, tmcRef, resolvedLocation.location)
  } finally baseClient.shutdown()

  private def visualizeResults(optimizedMap: OptimizedMapLayers,
                               inputLocation: LinearLocation,
                               tmcRef: TMCLocationReference,
                               resolvedLocation: LinearLocation): Unit = {
    println(s"""Input location: $inputLocation
               |Resolved location: $resolvedLocation
               |Location reference:""".stripMargin)

    XmlMarshallers.locationReferencingContainer
      .marshall(LocationReferencingContainer(Seq(tmcRef)), Console.out)

    val geometries =
      PropertyMaps(optimizedMap).geometry

    val Yellow = Color("#f7f431")
    val Green = Color("#58db58")
    val Blue = Color("#76bde8")

    val searchPointFeatures = Seq(
      Feature.point(coordinateInFriedenstrasse, SimpleStyleProperties().markerColor(Yellow)))
    val inputLocationFeatures = locationFeatures(geometries, inputLocation, Green, -5)
    val resolvedLocationFeatures = locationFeatures(geometries, resolvedLocation, Blue, 5)

    val allFeatures = searchPointFeatures ++ inputLocationFeatures ++ resolvedLocationFeatures

    val path = FileNameHelper.exampleJsonFileFor(TmcCreateAndResolveExample)

    val fos = new FileOutputStream(path)
    FeatureCollection(allFeatures).writePretty(fos)
    fos.close()

    println(s"""
               |A GeoJson representation of the resolved vertices is available in $path
               |""".stripMargin)
  }

  private def locationFeatures(geometries: PropertyMap[Vertex, PackedLineString],
                               location: LinearLocation,
                               color: Color,
                               shift: Double) =
    location.path.map { vertex =>
      val properties = SimpleStyleProperties()
        .stroke(color)
        .add("vertex", s"${vertex.tileId.value}:${vertex.index.value}")
      Feature.lineString(shiftNorthWest(geometries(vertex), shift), properties)
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
