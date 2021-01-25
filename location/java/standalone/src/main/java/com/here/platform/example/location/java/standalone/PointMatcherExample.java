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

import static java.lang.Double.parseDouble;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.StreamSupport.stream;

import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.ProximitySearch;
import com.here.platform.location.dataloader.core.Catalog;
import com.here.platform.location.dataloader.core.caching.CacheManager;
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

/** A point matcher based on the Location Library. */
public final class PointMatcherExample {

  /**
   * Given a trip, returns the matched points on the unrestricted road network.
   *
   * <p>A trip is a sequence of probe points.
   *
   * <p>For the sake of simplicity we do not take into consideration neither the heading nor the
   * direction of travel, so the result might be a Vertex that is not traversable.
   */
  private static Stream<Optional<ElementProjection<Vertex>>> matchTrip(
      final ProximitySearch<GeoCoordinate, Vertex> ps,
      final Stream<GeoCoordinate> trip,
      final double radiusInMeters) {
    return trip.map(
        point ->
            stream(ps.search(point, radiusInMeters).spliterator(), false)
                .min(comparingDouble(ElementProjection::distanceInMeters)));
  }

  public static void main(final String[] args) throws IOException {
    final StandaloneCatalogFactory catalogFactory = new StandaloneCatalogFactory();
    final CacheManager cacheManager = CacheManager.withLruCache();

    try {
      final Catalog optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 1293L);
      final Stream<GeoCoordinate> trip = loadTripFromCSVResource("/example_berlin_path.csv");

      final ProximitySearch<GeoCoordinate, Vertex> proximitySearch =
          ProximitySearches.vertices(optimizedMap, cacheManager);

      final double radiusInMeters = 10;
      final Stream<Optional<ElementProjection<Vertex>>> matchedPoints =
          matchTrip(proximitySearch, trip, radiusInMeters);

      matchedPoints.forEach(
          matchedPoint ->
              System.out.println(
                  matchedPoint.map(PointMatcherExample::format).orElse("Not Matched")));

    } finally {
      catalogFactory.terminate();
    }
  }

  private static String format(final ElementProjection<Vertex> ep) {
    return String.format(
        Locale.ROOT,
        "Matched on %s nearest=(%.4f,%.4f) distance=%.1f",
        ep.getElement(),
        ep.getNearest().getLatitude(),
        ep.getNearest().getLongitude(),
        ep.getDistanceInMeters());
  }

  private static Stream<GeoCoordinate> loadTripFromCSVResource(final String resourcePath)
      throws IOException {
    final CSVParser parser =
        CSVParser.parse(
            PointMatcherExample.class.getResourceAsStream(resourcePath),
            Charset.defaultCharset(),
            CSVFormat.DEFAULT);
    return stream(parser.spliterator(), false)
        .map(row -> new GeoCoordinate(parseDouble(row.get(0)), parseDouble(row.get(1))));
  }
}
