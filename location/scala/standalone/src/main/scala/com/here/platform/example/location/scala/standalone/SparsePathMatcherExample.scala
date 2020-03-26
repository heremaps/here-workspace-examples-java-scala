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
import com.here.platform.location.core.geospatial.{GeoCoordinate, LineStringOperations}
import com.here.platform.location.core.mapmatching.MatchedPath.Transition
import com.here.platform.location.core.mapmatching.{MatchResult, MatchedPath, OnRoad}
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.integration.optimizedmap.mapmatching.PathMatchers

object SparsePathMatcherExample extends App {
  import Helpers._
  val catalogFactory = new StandaloneCatalogFactory()
  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)

    val trip: Seq[GeoCoordinate] = loadTripFromCSVResource("/example_berlin_path_sparse.csv")

    println(s"Loaded trip with ${trip.length} points.")

    import GeoCoordinate.GeoCoordinatesOps

    val pathMatcher = PathMatchers.carPathMatcherWithTransitions(optimizedMap, cacheManager)

    val MatchedPath(matchResults, transitions) = pathMatcher.matchPath(trip)

    printMatchedPath(trip, matchResults, transitions, optimizedMap, cacheManager)
  } finally {
    catalogFactory.terminate()
  }

  private object Helpers {
    import au.id.jazzy.play.geojson._
    import com.here.platform.example.location.utils.Visualization._
    import com.here.platform.location.core.geospatial.Implicits._
    import com.here.platform.location.core.geospatial.LineStrings
    import com.here.platform.location.core.graph.PropertyMap
    import play.api.libs.json._

    import scala.collection.immutable

    implicit val lsOps = PackedLineString.PackedLineStringOps

    def loadTripFromCSVResource(s: String): Seq[GeoCoordinate] =
      CSVReader.open(new InputStreamReader(getClass.getResourceAsStream(s))).all.map {
        case List(lat, lon) => new GeoCoordinate(lat.toDouble, lon.toDouble)
      }

    def printMatchedPath(probePoints: Seq[GeoCoordinate],
                         matchResults: Seq[MatchResult[Vertex]],
                         transitions: Seq[Transition[Seq[Vertex]]],
                         optimizedMap: Catalog,
                         cacheManager: CacheManager): Unit = {
      val geometries = PropertyMaps.geometry(optimizedMap, cacheManager)

      val matchResultsAsFeatures =
        computeMatchResultsAsFeatures(probePoints, matchResults)

      val pathsAsFeatures =
        computePathsAsFeatures(matchResults, transitions, geometries)

      val json = Json.toJson(FeatureCollection(matchResultsAsFeatures ++ pathsAsFeatures))
      val path = FileNameHelper.exampleJsonFileFor(SparsePathMatcherExample).toPath
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
      * Computes geojson features for matched paths between matched points
      */
    private def computePathsAsFeatures(
        matchResults: Seq[MatchResult[Vertex]],
        transitions: Seq[Transition[Seq[Vertex]]],
        geometries: PropertyMap[Vertex, PackedLineString]): immutable.Seq[Feature[GeoCoordinate]] =
      transitions
        .flatMap {
          case MatchedPath.Transition(from, to, vertices) =>
            (matchResults(from), matchResults(to)) match {
              case (OnRoad(startProjection), OnRoad(endProjection)) =>
                val startVertex = startProjection.element
                val endVertex = endProjection.element

                val startFraction = startProjection.fraction
                val endFraction = endProjection.fraction

                if (startVertex == endVertex) {
                  val cut =
                    LineStrings.cut(geometries(startVertex), Seq((startFraction, endFraction))).head

                  Seq(createLineStringFeature(cut, Blue, from, to))
                } else {
                  val startCut =
                    LineStrings.cut(geometries(startVertex), Seq((startFraction, 1.0))).head
                  val endCut = LineStrings.cut(geometries(endVertex), Seq((0.0, endFraction))).head
                  val transitionGeometries = vertices.map(vertex => geometries(vertex))

                  val startCutFeature = createLineStringFeature(startCut, Blue, from, to)
                  val endCutFeature = createLineStringFeature(endCut, Blue, from, to)
                  val transitionFeatures =
                    transitionGeometries.map(createLineStringFeature(_, Green, from, to))

                  startCutFeature +: transitionFeatures :+ endCutFeature
                }
              case _ => Seq.empty
            }
        }
        .to[immutable.Seq]

    private def createLineStringFeature[LS: LineStringOperations](lineString: LS,
                                                                  color: Color,
                                                                  from: Int,
                                                                  to: Int): Feature[GeoCoordinate] =
      Feature(lineString, Some(Stroke(color) ++ Json.obj("[from, to]" -> Array(from, to))))
  }
}
