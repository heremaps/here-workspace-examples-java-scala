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

import java.io.InputStreamReader
import java.nio.file.Files

import com.github.tototoshi.csv.CSVReader
import com.here.hrn.HRN
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.core.mapmatching.{MatchResult, MatchedPath, OnRoad}
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.integration.optimizedmap.mapmatching.PathMatchers

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
    import au.id.jazzy.play.geojson._
    import com.here.platform.example.location.utils.Visualization._
    import com.here.platform.location.core.geospatial.Implicits._
    import com.here.platform.location.core.graph.PropertyMap
    import com.here.platform.location.inmemory.geospatial.PackedLineString.PackedLineStringOps
    import play.api.libs.json._

    import scala.collection.immutable

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

      val json = Json.toJson(FeatureCollection(matchResultsAsFeatures ++ pathsAsFeatures))
      val path = FileNameHelper.exampleJsonFileFor(PathMatcherExample).toPath
      Files.write(path, Json.prettyPrint(json).getBytes)
      println(s"\nA GeoJson representation of the result is available in $path\n")
    }

    /**
      * Computes geojson features for probes, corresponding projected points and linestrings
      * between them
      */
    private def computeMatchResultsAsFeatures(
        probePoints: Seq[GeoCoordinate],
        matchResults: Seq[MatchResult[Vertex]]): immutable.Seq[Feature[GeoCoordinate]] =
      matchResults
        .zip(probePoints)
        .flatMap {
          case (OnRoad(ep), pp) =>
            Seq(
              Feature(Point(pp), Some(MarkerColor(Red))),
              Feature(Point(ep.nearest), Some(MarkerColor(Green))),
              Feature(LineString(immutable.Seq(pp, ep.nearest)), Some(Stroke(Blue)))
            )
          case (_, pp) => Seq(Feature(Point(pp), Some(JsObject.empty)))
        }
        .to[immutable.Seq]

    /**
      * Computes geojson features for vertices containing matched points
      */
    private def computePathsAsFeatures(
        matchResults: Seq[MatchResult[Vertex]],
        geometries: PropertyMap[Vertex, PackedLineString]): immutable.Seq[Feature[GeoCoordinate]] =
      matchResults
        .collect {
          case OnRoad(ep) =>
            Feature(geometries(ep.element), Some(JsObject.empty))
        }
        .to[immutable.Seq]
  }
}
