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

package com.here.platform.example.location.java.standalone;

import static java.util.stream.StreamSupport.stream;

import com.here.hrn.HRN;
import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.ProximitySearch;
import com.here.platform.location.core.graph.javadsl.DirectedGraph;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.dataloader.core.Catalog;
import com.here.platform.location.dataloader.core.caching.CacheManager;
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory;
import com.here.platform.location.inmemory.graph.Edge;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.AccessRestriction;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.Graphs;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import java.util.List;
import java.util.stream.Collectors;

public final class TurnRestrictionsExample {

  public static void main(final String[] args) {
    final StandaloneCatalogFactory catalogFactory = new StandaloneCatalogFactory();
    final CacheManager cacheManager = CacheManager.withLruCache();

    try {
      final Catalog optimizedMap =
          catalogFactory.create(
              HRN.fromString("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
              705L);

      final PropertyMap<Edge, Boolean> turnRestrictionsMap =
          PropertyMaps.turnRestrictions(
              optimizedMap,
              cacheManager,
              AccessRestriction.Automobile.union(AccessRestriction.Bus));

      final ProximitySearch<GeoCoordinate, Vertex> search =
          ProximitySearches.vertices(optimizedMap, cacheManager);
      final GeoCoordinate chausseestrSouth = new GeoCoordinate(52.5297909677433, 13.38406758553557);
      final List<Vertex> vertices =
          stream(search.search(chausseestrSouth, 10).spliterator(), false)
              .map(ElementProjection::element)
              .collect(Collectors.toList());
      assert vertices.size() == 2;

      final DirectedGraph<Vertex, Edge> routingGraph = Graphs.from(optimizedMap, cacheManager);

      for (final Vertex v : vertices) {
        routingGraph
            .getOutEdgeIterator(v)
            .forEachRemaining(
                e -> {
                  if (turnRestrictionsMap.get(e)) {
                    System.out.format("turn %s -> %s restricted%n", v, routingGraph.getTarget(e));
                  }
                });
      }
    } finally {
      catalogFactory.terminate();
    }
  }
}
