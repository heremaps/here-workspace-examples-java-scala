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

import au.id.jazzy.play.geojson._
import com.here.hrn.HRN
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.example.location.utils.Visualization._
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.core.graph.{DirectedGraph, PropertyMap}
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.{Edge, Vertex}
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.{Graphs, PropertyMaps}
import play.api.libs.json._

import scala.annotation.tailrec

/* Apply a simple graph algorithm (breadth first search) on the HERE road network topology and
 * visualize the output in a GeoJSON file
 */
object GraphExample extends App {
  /* Performs some iterations of breadth first search and returns the list of discovered vertices
   * at each iteration
   */
  def breadthFirstSearch[V, E](g: DirectedGraph[V, E], start: V, iterations: Int): List[List[V]] = {
    @tailrec
    def go(elems: List[V],
           visitedSet: Set[V],
           visited: List[List[V]],
           iterations: Int): List[List[V]] = {
      val discovered =
        elems
          .flatMap(v => g.outEdgeIterator(v).map(g.target))
          .filterNot(visitedSet.contains)
          .distinct
      if (discovered.isEmpty || iterations == 0)
        visited
      else
        go(discovered, visitedSet ++ discovered, discovered :: visited, iterations - 1)
    }

    go(List(start), Set(start), List(List(start)), iterations).reverse
  }

  val maxIterations = 20

  val catalogFactory = new StandaloneCatalogFactory()
  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)

    val pariserPlatz = GeoCoordinate(52.516364, 13.378870)

    val graph: DirectedGraph[Vertex, Edge] = Graphs.from(optimizedMap, cacheManager)

    val startVertex = ProximitySearches
      .vertices(optimizedMap, cacheManager)
      .search(pariserPlatz, 50)
      .minBy(_.distanceInMeters)
      .element

    val visitedVertices: List[List[Vertex]] = breadthFirstSearch(graph, startVertex, maxIterations)

    printResults(visitedVertices, optimizedMap, cacheManager)
  } finally {
    catalogFactory.terminate()
  }

  // Visualize the search tree using a fading color to represent the iterations
  def printResults(visitedVertices: List[List[Vertex]],
                   optimizedMap: Catalog,
                   cacheManager: CacheManager): Unit = {
    val geometries: PropertyMap[Vertex, PackedLineString] =
      PropertyMaps.geometry(optimizedMap, cacheManager)

    val geoJsonFeatures = visitedVertices.zipWithIndex
      .flatMap {
        case (vertices, depth) =>
          vertices.map { v =>
            Feature(
              geometries(v),
              Some(Stroke(redToYellowGradient(depth.toFloat, 0f, maxIterations.toFloat)))
            )
          }
      }

    val json = Json.toJson(FeatureCollection(geoJsonFeatures))
    val path = FileNameHelper.exampleJsonFileFor(GraphExample).toPath
    Files.write(path, Json.prettyPrint(json).getBytes)
    println("\nA GeoJson representation of the result is available in " + path + "\n")
  }
}
