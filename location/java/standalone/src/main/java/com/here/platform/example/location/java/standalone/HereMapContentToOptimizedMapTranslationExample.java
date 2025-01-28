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

package com.here.platform.example.location.java.standalone;

import static java.util.stream.StreamSupport.stream;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.ProximitySearch;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.geospatial.HereMapContentReference;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;

public final class HereMapContentToOptimizedMapTranslationExample {
  public static void main(final String[] args) {
    final GeoCoordinate berlinDowntown = new GeoCoordinate(52.5085, 13.3898);
    final double radiusInMeters = 50.0;

    final BaseClient baseClient = BaseClientJava.instance();

    try {
      final OptimizedMapLayers optimizedMap =
          OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
              .usingBaseClient(baseClient)
              .newInstance()
              .version(1293L);

      final ProximitySearch<GeoCoordinate, Vertex> proximitySearch =
          new ProximitySearches(optimizedMap).vertices();

      final PropertyMap<HereMapContentReference, Vertex> hereMapContentToOptimizedMap =
          new PropertyMaps(optimizedMap).hereMapContentReferenceToVertex();

      final PropertyMap<Vertex, HereMapContentReference> optimizedMapToHereMapContent =
          new PropertyMaps(optimizedMap).vertexToHereMapContentReference();

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
      baseClient.shutdown();
    }
  }
}
