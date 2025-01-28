/*
 * Copyright (C) 2017-2025 HERE Europe B.V.
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
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.core.graph.{DirectedGraph, PropertyMap}
import com.here.platform.location.inmemory.graph.{Edge, Vertex}
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.{
  AccessRestriction,
  Graphs,
  PropertyMaps
}
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{FeatureCollection, SimpleStyleProperties}

import java.io.FileOutputStream

object TurnRestrictionsExample extends App {
  val baseClient = BaseClient()

  try {
    val optimizedMap = OptimizedMapCatalog
      .from(OptimizedMap.v2.HRN)
      .usingBaseClient(baseClient)
      .newInstance
      .version(1293L)

    val propertyMaps = PropertyMaps(optimizedMap)
    val turnRestrictionsMap: PropertyMap[Edge, Boolean] =
      propertyMaps.turnRestrictions(AccessRestriction.Automobile union AccessRestriction.Bus)

    val search = ProximitySearches(optimizedMap).vertices
    val chausseestrSouth = GeoCoordinate(52.5297909677433, 13.38406758553557)
    val vertices = search.search(chausseestrSouth, 10).map(_.element)
    assert(vertices.size == 2)

    val routingGraph: DirectedGraph[Vertex, Edge] = Graphs(optimizedMap).forward

    val targetVerticesWithRestrictions = vertices.flatMap { v =>
      val edges = routingGraph.outEdgeIterator(v)

      edges.map { e =>
        (routingGraph.target(e), turnRestrictionsMap(e))
      }
    }

    val Red = Color("#e87676")
    val Gray = Color("#777777")
    val Blue = Color("#76bde8")

    val verticesWithColors = targetVerticesWithRestrictions.map {
      case (vertex, restricted) => (vertex, if (restricted) Red else Gray)
    } ++ vertices.map((_, Blue))

    val geometryPropertyMap = propertyMaps.geometry

    val featureCollection = verticesWithColors.foldLeft(FeatureCollection()) {
      case (fc, (vertex, color)) =>
        fc.lineString(
          geometryPropertyMap(vertex),
          SimpleStyleProperties().stroke(color)
        )
    }

    val path = FileNameHelper.exampleJsonFileFor(TurnRestrictionsExample)

    val fos = new FileOutputStream(path)
    featureCollection.writePretty(fos)
    fos.close()
    println("\nA GeoJson representation of the result is available in:\n" + path + "\n")
  } finally {
    baseClient.shutdown()
  }
}
