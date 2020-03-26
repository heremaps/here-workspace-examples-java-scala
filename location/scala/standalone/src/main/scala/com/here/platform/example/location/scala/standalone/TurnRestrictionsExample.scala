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

import au.id.jazzy.play.geojson
import com.here.hrn.HRN
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.example.location.utils.Visualization._
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.core.geospatial.Implicits._
import com.here.platform.location.core.graph.{DirectedGraph, PropertyMap}
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.{Edge, Vertex}
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.{
  AccessRestriction,
  Graphs,
  PropertyMaps
}
import play.api.libs.json.Json

import scala.collection.immutable

object TurnRestrictionsExample extends App {
  val catalogFactory = new StandaloneCatalogFactory()
  val cacheManager = CacheManager.withLruCache()

  try {
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)

    val turnRestrictionsMap: PropertyMap[Edge, Boolean] =
      PropertyMaps.turnRestrictions(optimizedMap,
                                    cacheManager,
                                    AccessRestriction.Automobile union AccessRestriction.Bus)

    val search = ProximitySearches.vertices(optimizedMap, cacheManager)
    val chausseestrSouth = GeoCoordinate(52.5297909677433, 13.38406758553557)
    val vertices = search.search(chausseestrSouth, 10).map(_.element)
    assert(vertices.size == 2)

    val routingGraph: DirectedGraph[Vertex, Edge] = Graphs.from(optimizedMap, cacheManager)

    val targetVerticesWithRestrictions = vertices.flatMap { v =>
      val edges = routingGraph.outEdgeIterator(v)

      edges.map { e =>
        (routingGraph.target(e), turnRestrictionsMap(e))
      }
    }

    val verticesWithColors = targetVerticesWithRestrictions.map {
      case (vertex, restricted) => (vertex, if (restricted) Red else Gray)
    } ++ vertices.map((_, Blue))

    val geometryPropertyMap = PropertyMaps.geometry(optimizedMap, cacheManager)

    val verticesJson = verticesWithColors
      .map {
        case (vertex, color) =>
          geojson.Feature(
            geometryPropertyMap(vertex),
            Some(Stroke(color))
          )
      }
      .to[immutable.Seq]

    val json = Json.toJson(geojson.FeatureCollection(verticesJson))
    val path = FileNameHelper.exampleJsonFileFor(TurnRestrictionsExample).toPath
    Files.write(path, Json.prettyPrint(json).getBytes)
    println("\nA GeoJson representation of the result is available in:\n" + path + "\n")
  } finally {
    catalogFactory.terminate()
  }
}
