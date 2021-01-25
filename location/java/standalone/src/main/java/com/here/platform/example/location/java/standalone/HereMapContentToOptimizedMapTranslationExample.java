/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.ProximitySearch;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.dataloader.core.Catalog;
import com.here.platform.location.dataloader.core.caching.CacheManager;
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.geospatial.HereMapContentReference;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;

public final class HereMapContentToOptimizedMapTranslationExample {
  public static void main(final String[] args) {
    final GeoCoordinate berlinDowntown = new GeoCoordinate(52.5085, 13.3898);
    final double radiusInMeters = 50.0;

    final StandaloneCatalogFactory catalogFactory = new StandaloneCatalogFactory();

    try {
      final CacheManager cacheManager = CacheManager.withLruCache();
      final Catalog optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 1293L);

      final ProximitySearch<GeoCoordinate, Vertex> proximitySearch =
          ProximitySearches.vertices(optimizedMap, cacheManager);

      final PropertyMap<HereMapContentReference, Vertex> hereMapContentToOptimizedMap =
          PropertyMaps.hereMapContentReferenceToVertex(optimizedMap, cacheManager);

      final PropertyMap<Vertex, HereMapContentReference> optimizedMapToHereMapContent =
          PropertyMaps.vertexToHereMapContentReference(optimizedMap, cacheManager);

      stream(proximitySearch.search(berlinDowntown, radiusInMeters).spliterator(), false)
          .limit(5)
          .forEach(
              projection -> {
                final HereMapContentReference hereMapContentReference =
                    optimizedMapToHereMapContent.get(projection.element());
                final Vertex vertex = hereMapContentToOptimizedMap.get(hereMapContentReference);
                System.out.println(
                    "                     Optimized Map vertex: " + projection.element());
                System.out.println(
                    " Translated to HERE Map Content reference: " + hereMapContentReference);
                System.out.println("   Translated back to Optimized Map vertex: " + vertex);
                System.out.println();
              });
    } finally {
      catalogFactory.terminate();
    }
  }
}
