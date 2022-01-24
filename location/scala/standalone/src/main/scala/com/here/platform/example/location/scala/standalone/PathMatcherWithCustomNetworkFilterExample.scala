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

import java.io.InputStreamReader

import com.github.tototoshi.csv.CSVReader
import com.here.platform.location.core.geospatial._
import com.here.platform.location.core.mapmatching._
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.{Graphs, PropertyMaps, RoadAccess}
import com.here.platform.location.integration.optimizedmap.mapmatching.{
  EmissionProbabilityStrategies,
  PathMatchers,
  TransitionProbabilityStrategies
}

object PathMatcherWithCustomNetworkFilterExample extends App {
  val catalogFactory = new StandaloneCatalogFactory()
  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 1293L)
    val trip: Seq[GeoCoordinate] = Helpers.loadTripFromCSVResource("/berlin_no_taxi.csv")

    println(s"Loaded trip with ${trip.length} points.")

    val accessibleToTaxi = PropertyMaps.roadAccess(optimizedMap, cacheManager, RoadAccess.Taxi)
    val accessibleToAutomobile =
      PropertyMaps.roadAccess(optimizedMap, cacheManager, RoadAccess.Automobile)
    val topologySegment = PropertyMaps.vertexToHereMapContentReference(optimizedMap, cacheManager)

    // Instantiate a path matcher on the filtered road network
    val pathMatcher = PathMatchers.newHMMPathMatcher[GeoCoordinate, Vertex, Seq[Vertex]](
      CandidateGenerators.fromProximitySearchWithUnknown[GeoCoordinate, Vertex](
        // Filter out search results on parts of a road segment that are not accessible to taxis
        new FilteredProximitySearch(
          ProximitySearches.vertexGeometrySegments(optimizedMap, cacheManager),
          PropertyMaps.geometry(optimizedMap, cacheManager),
          accessibleToTaxi,
          SinusoidalProjection
        )),
      EmissionProbabilityStrategies.usingDistance,
      TransitionProbabilityStrategies.distanceWithTransitions(
        Graphs.from(optimizedMap, cacheManager),
        PropertyMaps.length(optimizedMap, cacheManager),
        accessibleToTaxi,
        GreatCircleDistanceCalculator)
    )

    val verticesOnPath: Seq[Vertex] = pathMatcher
      .matchPath(trip)
      .results
      .flatMap {
        case OnRoad(e) => Some(e.element)
        case _ => None
      }
      .distinct

    println(s"The path goes through ${verticesOnPath.length} road segments:")
    verticesOnPath.foreach { v =>
      val taxiOrNot = if (accessibleToTaxi(v).forall(_.value)) "is" else "is not"
      val autoOrNot = if (accessibleToAutomobile(v).forall(_.value)) "is" else "is not"
      println(
        s"- Vertex: $v corresponds to road segment: ${topologySegment(v)} that $autoOrNot accessible to Autos and $taxiOrNot accessible to Taxis.")
    }
  } finally {
    catalogFactory.terminate()
  }

  private object Helpers {
    def loadTripFromCSVResource(s: String): Seq[GeoCoordinate] =
      CSVReader.open(new InputStreamReader(getClass.getResourceAsStream(s))).all.map {
        case List(lat, lon) => new GeoCoordinate(lat.toDouble, lon.toDouble)
      }
  }
}
