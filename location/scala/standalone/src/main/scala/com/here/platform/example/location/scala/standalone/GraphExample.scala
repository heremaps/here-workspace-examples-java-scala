/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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

import java.io.FileOutputStream
import com.here.platform.location.core.geospatial.{GeoCoordinate, ProximitySearch}
import com.here.platform.location.core.graph.{DirectedGraph, PropertyMap}
import com.here.platform.data.client.base.scaladsl.BaseClient
import com.here.platform.example.location.scala.standalone.utils.FileNameHelper
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.{Edge, Vertex}
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.{Graphs, PropertyMaps}
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}

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

  val baseClient = BaseClient()
  try {
    val optimizedMap: OptimizedMapLayers =
      OptimizedMapCatalog
        .from(OptimizedMap.v2.HRN)
        .usingBaseClient(baseClient)
        .newInstance
        .version(1293L)

    val pariserPlatz = GeoCoordinate(52.516364, 13.378870)

    val graphs: Graphs = Graphs(optimizedMap)
    val graph: DirectedGraph[Vertex, Edge] = graphs.forward

    val startVertex = ProximitySearches(optimizedMap).vertices
      .search(pariserPlatz, 50)
      .minBy(_.distanceInMeters)
      .element

    val visitedVertices: List[List[Vertex]] = breadthFirstSearch(graph, startVertex, maxIterations)

    printResults(visitedVertices, optimizedMap)
  } finally {
    baseClient.shutdown()
  }

  // Visualize the search tree using a fading color to represent the iterations
  def printResults(visitedVertices: List[List[Vertex]], optimizedMap: OptimizedMapLayers): Unit = {
    val geometries: PropertyMap[Vertex, PackedLineString] =
      PropertyMaps(optimizedMap).geometry

    val features = visitedVertices.zipWithIndex
      .flatMap {
        case (vertices, depth) =>
          vertices.map { v =>
            // Creating a red to yellow gradient for a geometry
            Feature.lineString(geometries(v),
                               SimpleStyleProperties().stroke(Color.hsb(depth * 3.0, 0.8, 0.8)))
          }
      }

    val path = FileNameHelper.exampleJsonFileFor(GraphExample)

    val fos = new FileOutputStream(path)
    FeatureCollection(features).writePretty(fos)
    fos.close()

    println("\nA GeoJson representation of the result is available in " + path + "\n")
  }
}
