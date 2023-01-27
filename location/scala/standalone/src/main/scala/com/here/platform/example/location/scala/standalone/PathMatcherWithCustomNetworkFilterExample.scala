/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
import com.here.platform.data.client.base.scaladsl.BaseClient
import com.here.platform.location.core.geospatial._
import com.here.platform.location.core.mapmatching._
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.{Graphs, PropertyMaps, RoadAccess}
import com.here.platform.location.core.geospatial.Implicits._
import com.here.platform.location.integration.optimizedmap.mapmatching.{
  EmissionProbabilityStrategies,
  PathMatchers,
  TransitionProbabilityStrategies
}

object PathMatcherWithCustomNetworkFilterExample extends App {
  val baseClient = BaseClient()

  try {
    val optimizedMap: OptimizedMapLayers =
      OptimizedMapCatalog
        .from(OptimizedMap.v2.HRN)
        .usingBaseClient(baseClient)
        .newInstance
        .version(1293L)
    val trip: Seq[GeoCoordinate] = Helpers.loadTripFromCSVResource("/berlin_no_taxi.csv")

    println(s"Loaded trip with ${trip.length} points.")

    val accessibleToTaxi = PropertyMaps(optimizedMap).roadAccess(RoadAccess.Taxi)
    val accessibleToAutomobile =
      PropertyMaps(optimizedMap).roadAccess(RoadAccess.Automobile)
    val topologySegment = PropertyMaps(optimizedMap).vertexToHereMapContentReference

    // Instantiate a path matcher on the filtered road network
    val pathMatcher = PathMatchers.newHMMPathMatcher[GeoCoordinate, Vertex, Seq[Vertex]](
      CandidateGenerators.fromProximitySearchWithUnknown[GeoCoordinate, Vertex](
        // Filter out search results on parts of a road segment that are not accessible to taxis
        new FilteredProximitySearch(
          ProximitySearches(optimizedMap).vertexGeometrySegments,
          PropertyMaps(optimizedMap).geometry,
          accessibleToTaxi,
          SinusoidalProjection
        )),
      EmissionProbabilityStrategies.usingDistance,
      TransitionProbabilityStrategies.distanceWithTransitions(
        Graphs(optimizedMap).forward,
        PropertyMaps(optimizedMap).geometry,
        PropertyMaps(optimizedMap).length,
        accessibleToTaxi,
        GreatCircleDistanceCalculator
      )
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
    baseClient.shutdown()
  }

  private object Helpers {
    def loadTripFromCSVResource(s: String): Seq[GeoCoordinate] =
      CSVReader.open(new InputStreamReader(getClass.getResourceAsStream(s))).all.map {
        case List(lat, lon) => new GeoCoordinate(lat.toDouble, lon.toDouble)
      }
  }
}
