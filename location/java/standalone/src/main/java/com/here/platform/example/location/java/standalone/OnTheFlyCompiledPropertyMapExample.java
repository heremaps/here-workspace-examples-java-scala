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

import static java.lang.String.format;
import static java.util.stream.StreamSupport.stream;

import com.here.hrn.HRN;
import com.here.platform.location.compilation.heremapcontent.javadsl.AttributeAccessor;
import com.here.platform.location.compilation.heremapcontent.javadsl.AttributeAccessors;
import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.graph.javadsl.RangeBasedProperty;
import com.here.platform.location.core.graph.javadsl.RangeBasedPropertyMap;
import com.here.platform.location.dataloader.core.Catalog;
import com.here.platform.location.dataloader.core.caching.CacheManager;
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import com.here.schema.rib.v2.common_attributes.SpeedLimitAttribute;
import com.here.schema.rib.v2.navigation_attributes_partition.NavigationAttributesPartition;

public final class OnTheFlyCompiledPropertyMapExample {
  public static void main(final String[] args) {
    final StandaloneCatalogFactory catalogFactory = new StandaloneCatalogFactory();
    final CacheManager cacheManager = CacheManager.withLruCache();

    try {
      final Catalog optimizedMap =
          catalogFactory.create(
              HRN.fromString("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
              705L);

      final GeoCoordinate brandenburgerTor = new GeoCoordinate(52.516268, 13.377700);
      final AttributeAccessor<NavigationAttributesPartition, Integer> speedLimitAccessor =
          AttributeAccessors.forHereMapContentSegmentAnchor(
              NavigationAttributesPartition::speedLimit, SpeedLimitAttribute::value);

      final Iterable<ElementProjection<Vertex>> elementProjections =
          ProximitySearches.vertices(optimizedMap, cacheManager).search(brandenburgerTor, 1000.0);
      final Catalog hereMapContent =
          optimizedMap.resolveDependency(HRN.fromString("hrn:here:data::olp-here:rib-2"));

      final RangeBasedPropertyMap<Vertex, Integer> propertyMap =
          PropertyMaps.navigationAttribute(
              optimizedMap, "speed-limit", hereMapContent, cacheManager, speedLimitAccessor);

      elementProjections.forEach(
          ep -> System.out.println(printSpeedLimitForVertex(ep.element(), propertyMap)));

    } finally {
      catalogFactory.terminate();
    }
  }

  private static String printSpeedLimitForVertex(
      final Vertex v, final RangeBasedPropertyMap<Vertex, Integer> speedLimitPropertyMap) {
    return format(
        "Vertex %7d: [%s]", v.index(), printSpeedLimitRanges(speedLimitPropertyMap.get(v)));
  }

  private static String printSpeedLimitRanges(
      final Iterable<RangeBasedProperty<Integer>> rangeBasedProperties) {
    return stream(rangeBasedProperties.spliterator(), false)
        .map(r -> format("%3.0f%% -> %3.0f%%:%3d km/h", r.start() * 100, r.end() * 100, r.value()))
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }
}
