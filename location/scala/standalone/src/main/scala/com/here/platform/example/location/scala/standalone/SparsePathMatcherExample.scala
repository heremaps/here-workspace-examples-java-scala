/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import java.io.{FileOutputStream, InputStreamReader}

import com.github.tototoshi.csv.CSVReader
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.location.core.geospatial.{GeoCoordinate, LineString, LineStringOperations}
import com.here.platform.location.core.mapmatching.MatchedPath.Transition
import com.here.platform.location.core.mapmatching.{MatchResult, MatchedPath, OnRoad}
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.integration.optimizedmap.mapmatching.PathMatchers
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}

object SparsePathMatcherExample extends App {
  import Helpers._
  val catalogFactory = new StandaloneCatalogFactory()
  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 1293L)

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
    private val Red = Color("#e87676")
    private val Green = Color("#58db58")
    private val Blue = Color("#76bde8")

    import com.here.platform.location.core.geospatial.LineStrings
    import com.here.platform.location.core.graph.PropertyMap

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

      val path = FileNameHelper.exampleJsonFileFor(SparsePathMatcherExample)

      val fos = new FileOutputStream(path)
      FeatureCollection(matchResultsAsFeatures ++ pathsAsFeatures).writePretty(fos)
      fos.close()

      println(s"\nA GeoJson representation of the result is available in $path\n")
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
      * Computes geojson features for matched paths between matched points
      */
    private def computePathsAsFeatures(
        matchResults: Seq[MatchResult[Vertex]],
        transitions: Seq[Transition[Seq[Vertex]]],
        geometries: PropertyMap[Vertex, PackedLineString]): Seq[Feature] =
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

    private def createLineStringFeature[LS: LineStringOperations](lineString: LS,
                                                                  color: Color,
                                                                  from: Int,
                                                                  to: Int): Feature =
      Feature.lineString(lineString,
                         SimpleStyleProperties().stroke(color).add("[from, to]", Array(from, to)))
  }
}
