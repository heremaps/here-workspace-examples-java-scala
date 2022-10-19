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

package com.here.platform.example.location.java.standalone;

import static java.util.stream.StreamSupport.stream;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.ProximitySearch;
import com.here.platform.location.core.graph.javadsl.DirectedGraph;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.inmemory.graph.Edge;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.AccessRestriction;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.Graphs;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import java.util.List;
import java.util.stream.Collectors;

public final class TurnRestrictionsExample {

  public static void main(final String[] args) {
    final BaseClient baseClient = BaseClientJava.instance();

    try {
      final OptimizedMapLayers optimizedMap =
          OptimizedMapCatalog.newBuilder(OptimizedMap.v2.HRN).build(baseClient).version(1293L);

      final PropertyMap<Edge, Boolean> turnRestrictionsMap =
          new PropertyMaps(optimizedMap)
              .turnRestrictions(AccessRestriction.Automobile.union(AccessRestriction.Bus));

      final ProximitySearch<GeoCoordinate, Vertex> search =
          new ProximitySearches(optimizedMap).vertices();
      final GeoCoordinate chausseestrSouth = new GeoCoordinate(52.5297909677433, 13.38406758553557);
      final List<Vertex> vertices =
          stream(search.search(chausseestrSouth, 10).spliterator(), false)
              .map(ElementProjection::element)
              .collect(Collectors.toList());
      assert vertices.size() == 2;

      final DirectedGraph<Vertex, Edge> routingGraph = new Graphs(optimizedMap).forward();

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
      baseClient.shutdown();
    }
  }
}
