/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
import static java.util.stream.StreamSupport.stream;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.mapmatching.MatchResult;
import com.here.platform.location.core.mapmatching.OnRoad;
import com.here.platform.location.core.mapmatching.javadsl.MatchResults;
import com.here.platform.location.core.mapmatching.javadsl.MatchedPath;
import com.here.platform.location.core.mapmatching.javadsl.PathMatcher;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.mapmatching.javadsl.PathMatchers;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

public final class SparsePathMatcherExample {

  public static void main(final String[] args) throws IOException {
    final BaseClient baseClient = BaseClientJava.instance();

    try {
      final OptimizedMapLayers optimizedMap =
          OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
              .usingBaseClient(baseClient)
              .newInstance()
              .version(1293L);
      final Stream<GeoCoordinate> trip = loadTripFromCSVResource("/example_berlin_path_sparse.csv");

      final PathMatcher<GeoCoordinate, Vertex, List<Vertex>> pathMatcher =
          new PathMatchers(optimizedMap).carPathMatcherWithTransitions();

      final MatchedPath<Vertex, List<Vertex>> matchedPath =
          pathMatcher.matchPath(trip.collect(Collectors.toList()));
      matchedPath.results().forEach(r -> System.out.println(format(r)));

    } finally {
      baseClient.shutdown();
    }
  }

  private static String format(final MatchResult<Vertex> matchResult) {
    if (MatchResults.isUnknown(matchResult)) {
      return "Not matched";
    } else if (MatchResults.isOffRoad(matchResult)) {
      return "Matched offroad";
    } else {
      final OnRoad<Vertex> onRoad = (OnRoad<Vertex>) matchResult;
      return String.format(
          Locale.ROOT,
          "Matched on %s nearest=(%.4f,%.4f) distance=%.1f",
          onRoad.elementProjection().getElement(),
          onRoad.elementProjection().getNearest().getLatitude(),
          onRoad.elementProjection().getNearest().getLongitude(),
          onRoad.elementProjection().getDistanceInMeters());
    }
  }

  private static Stream<GeoCoordinate> loadTripFromCSVResource(final String resourcePath)
      throws IOException {
    final CSVParser parser =
        CSVParser.parse(
            SparsePathMatcherExample.class.getResourceAsStream(resourcePath),
            Charset.defaultCharset(),
            CSVFormat.DEFAULT);
    return stream(parser.spliterator(), false)
        .map(row -> new GeoCoordinate(parseDouble(row.get(0)), parseDouble(row.get(1))));
  }
}
