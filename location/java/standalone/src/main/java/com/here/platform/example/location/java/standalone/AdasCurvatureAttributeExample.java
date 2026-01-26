/*
 * Copyright (C) 2017-2026 HERE Europe B.V.
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

import static java.lang.Math.*;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.LineString;
import com.here.platform.location.core.geospatial.javadsl.LineStringHolder;
import com.here.platform.location.core.geospatial.javadsl.LineStrings;
import com.here.platform.location.core.graph.javadsl.DirectedGraph;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.core.mapmatching.MatchResult;
import com.here.platform.location.core.mapmatching.MatchedPath.Transition;
import com.here.platform.location.core.mapmatching.OnRoad;
import com.here.platform.location.core.mapmatching.javadsl.MatchedPath;
import com.here.platform.location.inmemory.graph.Edge;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.Graphs;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import com.here.platform.location.integration.optimizedmap.mapmatching.javadsl.PathMatchers;
import com.here.platform.location.io.javadsl.Color;
import com.here.platform.location.io.javadsl.geojson.FeatureCollection;
import com.here.platform.location.io.javadsl.geojson.SimpleStyleProperties;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import scala.Tuple2;

public final class AdasCurvatureAttributeExample {

  public static void main(final String[] args) {
    final BaseClient baseClient = BaseClientJava.instance();

    try {
      final OptimizedMapLayers optimizedMap =
          OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
              .usingBaseClient(baseClient)
              .newInstance()
              .version(7647L);

      PropertyMaps propertyMaps = new PropertyMaps(optimizedMap);

      PropertyMaps.AdasAttributes adas = propertyMaps.adasAttributes();

      DirectedGraph<Vertex, Edge> graph = new Graphs(optimizedMap).forward();
      List<Vertex> vertices =
          verticesFromPath(
              optimizedMap,
              List.of(
                  new GeoCoordinate(46.517259, 10.320718),
                  new GeoCoordinate(46.515857, 10.317518),
                  new GeoCoordinate(46.517532, 10.315887),
                  new GeoCoordinate(46.516155, 10.313913),
                  new GeoCoordinate(46.515014, 10.315184),
                  new GeoCoordinate(46.514859, 10.316852),
                  new GeoCoordinate(46.513499, 10.318810)));
      List<Edge> edges = new ArrayList<>();
      for (int n = 0; n < vertices.size() - 1; n++) {
        Vertex source = vertices.get(n);
        Vertex target = vertices.get(n + 1);
        graph.getEdgeIterator(source, target).forEachRemaining(edges::add);
      }

      PropertyMap<Vertex, LineStringHolder<GeoCoordinate>> geometry = propertyMaps.geometry();

      FeatureCollection featureCollection = new FeatureCollection();

      vertices.forEach(
          vertex ->
              featureCollection.lineString(
                  geometry.get(vertex),
                  new SimpleStyleProperties().strokeWidth(10).stroke(Color.GRAY)));

      vertices.forEach(
          vertex ->
              featureCollection.lineStringPoints(
                  geometry.get(vertex),
                  adas.curvature().get(vertex),
                  pointBasedProperty ->
                      new SimpleStyleProperties()
                          .markerColor(toColor(pointBasedProperty.value()))
                          .markerSize("small")));

      PropertyMap<Vertex, Double> length = propertyMaps.length();

      edges.forEach(
          edge -> {
            Vertex source = graph.getSource(edge);
            Vertex target = graph.getTarget(edge);
            List<GeoCoordinate> lineString = new ArrayList<>();
            lineString.addAll(
                lineStringPart(geometry.get(source), length.get(source), -10.0).getPoints());
            lineString.addAll(
                lineStringPart(geometry.get(target), length.get(target), 10.0).getPoints());
            featureCollection.lineString(
                new LineString<>(lineString),
                new SimpleStyleProperties()
                    .stroke(toColor(adas.edgeCurvature().get(edge)))
                    .strokeWidth(6));
          });

      File path = exampleJsonFileFor(AdasCurvatureAttributeExample.class);
      try (OutputStream os = new FileOutputStream(path)) {
        featureCollection.writePretty(os);
      } catch (IOException e) {
        e.printStackTrace();
      }
      System.out.printf("\nA GeoJson representation of the result is available in %s\n", path);
    } finally {
      baseClient.shutdown();
    }
  }

  private static Color toColor(int curvature) {
    // Converting curvature value to radius in meters, see:
    // https://developer.here.com/documentation/here-map-content/dev_guide/topics-attributes/curvature.html
    double radius = abs(1000000.0 / curvature);
    // Gradient from red to green depending on road curvature radius, considering as green all
    // radius above 150 meters.
    return Color.hsb(min(radius, 150.0), 0.9, 0.8);
  }

  private static Color toColor(OptionalInt curvature) {
    return curvature.isEmpty() ? Color.BLACK : toColor(curvature.getAsInt());
  }

  private static LineStringHolder<GeoCoordinate> lineStringPart(
      LineStringHolder<GeoCoordinate> geometry, double lengthMeters, double offsetMeters) {
    double fraction = offsetMeters / max(lengthMeters, abs(offsetMeters));
    LineStrings<LineStringHolder<GeoCoordinate>, GeoCoordinate> lineStrings =
        LineStrings.getInstance();
    return lineStrings
        .cut(
            geometry,
            fraction >= 0
                ? Collections.singletonList(new Tuple2<>(0.0, fraction))
                : Collections.singletonList(new Tuple2<>(1.0 + fraction, 1.0)))
        .get(0);
  }

  private static File exampleJsonFileFor(Class<?> clazz) {
    File outputDir = new File(System.getProperty("java.io.tmpdir"), "example_output");
    outputDir.mkdir();
    return new File(outputDir, clazz.getCanonicalName() + ".json");
  }

  public static List<Vertex> verticesFromPath(
      OptimizedMapLayers optimizedMap, List<GeoCoordinate> path) {
    MatchedPath<Vertex, List<Vertex>> matchedPath =
        new PathMatchers(optimizedMap)
            .<GeoCoordinate>carPathMatcherWithTransitions()
            .matchPath(path);
    List<MatchResult<Vertex>> results = matchedPath.results();
    List<Transition<List<Vertex>>> transitions = matchedPath.transitions();

    if (results.isEmpty() || !results.stream().allMatch(r -> r instanceof OnRoad))
      throw new IllegalArgumentException();

    Stream<Vertex> firstVertex = Stream.of(vertex(results.get(0)));
    Stream<Vertex> transitionVertices =
        transitions
            .stream()
            .flatMap(
                transition ->
                    Stream.concat(
                        transition.value().stream(),
                        Stream.of(vertex(results.get(transition.to())))));
    return Stream.concat(firstVertex, transitionVertices).distinct().collect(Collectors.toList());
  }

  private static Vertex vertex(MatchResult<Vertex> result) {
    return ((OnRoad<Vertex>) result).elementProjection().element();
  }
}
