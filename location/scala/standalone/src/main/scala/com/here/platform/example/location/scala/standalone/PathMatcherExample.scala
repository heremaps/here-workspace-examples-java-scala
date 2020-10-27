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

import java.io.{File, FileOutputStream, InputStreamReader}

import com.github.tototoshi.csv.CSVReader
import com.here.hrn.HRN
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.location.core.geospatial.{GeoCoordinate, LineString}
import com.here.platform.location.core.graph.PropertyMap
import com.here.platform.location.core.mapmatching.{MatchResult, MatchedPath, OnRoad}
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.integration.optimizedmap.mapmatching.PathMatchers
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}

object PathMatcherExample extends App {
  import Helpers._
  val catalogFactory = new StandaloneCatalogFactory()
  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)

    val trip: Seq[GeoCoordinate] = loadTripFromCSVResource("/example_berlin_path.csv")

    println(s"Loaded trip with ${trip.length} points.")

    val pathMatcher =
      PathMatchers.carPathMatcher[GeoCoordinate](optimizedMap, cacheManager)

    val MatchedPath(matchResults, transitions) = pathMatcher.matchPath(trip)
    assert(matchResults.nonEmpty)
    assert(transitions.isEmpty)

    printMatchedPath(trip, matchResults, optimizedMap, cacheManager)
  } finally {
    catalogFactory.terminate()
  }

  private object Helpers {
    val Red = Color("#e87676")
    val Green = Color("#58db58")
    val Blue = Color("#76bde8")

    def loadTripFromCSVResource(s: String): Seq[GeoCoordinate] =
      CSVReader.open(new InputStreamReader(getClass.getResourceAsStream(s))).all.map {
        case List(lat, lon) => new GeoCoordinate(lat.toDouble, lon.toDouble)
      }

    def printMatchedPath(probePoints: Seq[GeoCoordinate],
                         matchResults: Seq[MatchResult[Vertex]],
                         optimizedMap: Catalog,
                         cacheManager: CacheManager): Unit = {
      val geometries = PropertyMaps.geometry(optimizedMap, cacheManager)

      val matchResultsAsFeatures =
        computeMatchResultsAsFeatures(probePoints, matchResults)

      val pathsAsFeatures =
        computePathsAsFeatures(matchResults, geometries)

      val geojsonFile: File = FileNameHelper.exampleJsonFileFor(PathMatcherExample)
      val fos = new FileOutputStream(geojsonFile)
      FeatureCollection(matchResultsAsFeatures ++ pathsAsFeatures).writePretty(fos)
      fos.close()

      println(s"\nA GeoJson representation of the result is available in $geojsonFile\n")
    }

    /**
      * Computes geojson features for probes, corresponding projected points and linestrings
      * between them
      */
    private def computeMatchResultsAsFeatures(
        probePoints: Seq[GeoCoordinate],
        matchResults: Seq[MatchResult[Vertex]]): Seq[Feature] =
      matchResults
        .zip(probePoints)
        .flatMap {
          case (OnRoad(ep), pp) =>
            Seq(
              Feature.point(pp, SimpleStyleProperties().markerColor(Red)),
              Feature.point(ep.nearest, SimpleStyleProperties().markerColor(Green)),
              Feature.lineString(LineString(Seq(pp, ep.nearest)),
                                 SimpleStyleProperties().stroke(Blue))
            )
          case (_, pp) => Seq(Feature.point(pp))
        }

    /**
      * Computes geojson features for vertices containing matched points
      */
    private def computePathsAsFeatures(
        matchResults: Seq[MatchResult[Vertex]],
        geometries: PropertyMap[Vertex, PackedLineString]): Seq[Feature] =
      matchResults
        .collect { case OnRoad(ep) => Feature.lineString(geometries(ep.element)) }
  }
}
