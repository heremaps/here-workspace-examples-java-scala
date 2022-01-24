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

import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.ProximitySearch;
import com.here.platform.location.core.graph.javadsl.RangeBasedProperty;
import com.here.platform.location.core.graph.javadsl.RangeBasedPropertyMap;
import com.here.platform.location.dataloader.core.Catalog;
import com.here.platform.location.dataloader.core.caching.CacheManager;
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import com.here.platform.location.integration.optimizedmap.roadattributes.FunctionalClass;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class FunctionalClassExample {
  public static void main(final String[] args) {
    final StandaloneCatalogFactory catalogFactory = new StandaloneCatalogFactory();
    final CacheManager cacheManager = CacheManager.withLruCache();

    try {
      final Catalog optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 1293L);

      final PropertyMaps.RoadAttributes roadAttributes =
          new PropertyMaps.RoadAttributes(optimizedMap, cacheManager);
      final RangeBasedPropertyMap<Vertex, FunctionalClass> functionalClass =
          roadAttributes.functionalClass();

      final ProximitySearch<GeoCoordinate, Vertex> search =
          ProximitySearches.vertices(optimizedMap, cacheManager);

      final GeoCoordinate messeNord = new GeoCoordinate(52.506671, 13.282895);
      final double radiusInMeters = 50.0;

      final List<Vertex> vertices =
          stream(search.search(messeNord, radiusInMeters).spliterator(), false)
              .map(ElementProjection::element)
              .collect(Collectors.toList());

      for (final Vertex vertex : vertices) {
        printFunctionalClassRanges(vertex, functionalClass.get(vertex));
      }
    } finally {
      catalogFactory.terminate();
    }
  }

  private static void printFunctionalClassRanges(
      final Vertex vertex, final Iterable<RangeBasedProperty<FunctionalClass>> ranges) {
    System.out.println(String.format("Vertex %7d", vertex.index()));
    ranges.forEach(
        range ->
            System.out.println(
                String.format(
                    Locale.ROOT, "  %.2f -> %.2f: %s", range.start(), range.end(), range.value())));
  }
}
