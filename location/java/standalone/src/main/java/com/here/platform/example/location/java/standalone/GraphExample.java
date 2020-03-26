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

import static java.util.Comparator.comparingDouble;
import static java.util.stream.StreamSupport.stream;

import com.here.hrn.HRN;
import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.graph.javadsl.DirectedGraph;
import com.here.platform.location.dataloader.core.Catalog;
import com.here.platform.location.dataloader.core.caching.CacheManager;
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory;
import com.here.platform.location.inmemory.graph.Edge;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.Graphs;
import java.util.*;

public final class GraphExample {
  public static void main(final String[] args) {

    final int nodesToVisit = 20;

    final StandaloneCatalogFactory catalogFactory = new StandaloneCatalogFactory();
    final CacheManager cacheManager = CacheManager.withLruCache();

    try {
      final Catalog optimizedMap =
          catalogFactory.create(
              HRN.fromString("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
              705L);

      final GeoCoordinate pariserPlatz = new GeoCoordinate(52.516364, 13.378870);

      final DirectedGraph<Vertex, Edge> graph = Graphs.from(optimizedMap, cacheManager);

      final Optional<ElementProjection<Vertex>> startVertex =
          stream(
                  ProximitySearches.vertices(optimizedMap, cacheManager)
                      .search(pariserPlatz, 50)
                      .spliterator(),
                  false)
              .min(comparingDouble(ElementProjection::distanceInMeters));

      startVertex.ifPresent(
          v -> breadthFirstVisit(graph, v.element(), nodesToVisit).forEach(System.out::println));

    } finally {
      catalogFactory.terminate();
    }
  }

  private static Iterable<Vertex> breadthFirstVisit(
      final DirectedGraph<Vertex, Edge> graph, final Vertex root, int numberOfNodes) {
    final Set<Vertex> visited = new LinkedHashSet<>();
    final Queue<Vertex> frontier = new LinkedList<>();
    frontier.add(root);
    while (numberOfNodes >= 0 && !frontier.isEmpty()) {
      final Vertex elem = frontier.remove();
      visited.add(elem);
      graph
          .getOutEdgeIterator(elem)
          .forEachRemaining(
              e -> {
                final Vertex v = graph.getTarget(e);
                if (!visited.contains(v)) {
                  frontier.add(v);
                }
              });
      numberOfNodes -= 1;
    }
    return visited;
  }
}
