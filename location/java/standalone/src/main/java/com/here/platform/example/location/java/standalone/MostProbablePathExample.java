/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
 *
 * The following rights to redistribution and use the software example in
 * source and binary forms, with or without modification, are granted to
 * you under copyrights provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * For clarification, no licenses to any patents are granted under this license.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.here.platform.example.location.java.standalone;

import static com.here.platform.location.core.Utils.normalizeLongitude;
import static java.lang.Math.*;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.StreamSupport.stream;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.javadsl.GeoCoordinates;
import com.here.platform.location.core.geospatial.javadsl.LineStringHolder;
import com.here.platform.location.core.graph.javadsl.*;
import com.here.platform.location.inmemory.graph.Edge;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.RoadAccess;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.Graphs;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import com.here.platform.location.integration.optimizedmap.roadattributes.FunctionalClass;
import java.util.*;
import java.util.function.Predicate;

// Determine the Most Probable Path from a starting vertex.
//
// This example builds on concepts of the US6405128 patent and on the HERE ADAS Research platform.
//
// A "most probable path" is identified following a greedy rule considering:
//   1. Change in functional class
//   2. Angle between roads
//
// The algorithm is stateless and will usually not be able to exit roundabouts, nonetheless it
// should provide ~87% accuracy on a turn by turn base.
//
public final class MostProbablePathExample {

  public static void main(final String[] args) {
    final BaseClient baseClient = BaseClientJava.instance();
    try {
      final OptimizedMapLayers optimizedMap =
          OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
              .usingBaseClient(baseClient)
              .newInstance()
              .version(1293L);
      PropertyMaps propertyMaps = new PropertyMaps(optimizedMap);

      // A mapping from Vertices to LineString of the underlying road geometry
      final PropertyMap<Vertex, LineStringHolder<GeoCoordinate>> geometryPropertyMap =
          propertyMaps.geometry();

      // The length of a Vertex (road segment)
      final PropertyMap<Vertex, Double> lengthPropertyMap = propertyMaps.length();

      final RangeBasedPropertyMap<Vertex, FunctionalClass> functionalClassPropertyMap =
          propertyMaps.roadAttributes().functionalClass();

      final RangeBasedPropertyMap<Vertex, Boolean> accessibleByCarPropertyMap =
          propertyMaps.roadAccess(RoadAccess.Automobile);

      final DirectedGraph<Vertex, Edge> graph = new Graphs(optimizedMap).forward();

      // Load the graph and apply a filter to only navigate links that are accessible by cars
      final FilteredGraph<Vertex, Edge> filteredGraph =
          new FilteredGraph<>(graph, filterBy(graph, accessibleByCarPropertyMap));

      final GeoCoordinate startPoint = new GeoCoordinate(52.50547, 13.37116);
      final double searchRadiusInMeters = 30.0;

      // Select a starting drivable Vertex
      final Vertex start =
          stream(
                  new ProximitySearches(optimizedMap)
                      .vertices()
                      .search(startPoint, searchRadiusInMeters)
                      .spliterator(),
                  false)
              .filter(e -> checkBooleanRangedProperty(e.element(), accessibleByCarPropertyMap))
              .min(comparingDouble(ElementProjection::distanceInMeters))
              .get()
              .element();

      final ProbabilityStrategy probabilityFunction =
          probability(functionalClassPropertyMap, geometryPropertyMap);

      final double maximumMppLengthInMeters = 10000.0;
      final List<Vertex> mostProbablePathSegments =
          mostProbablePath(
              start,
              maximumMppLengthInMeters,
              filteredGraph,
              lengthPropertyMap,
              probabilityFunction);

      for (final Vertex v : mostProbablePathSegments) {
        System.out.println(v);
      }

    } finally {
      baseClient.shutdown();
    }
  }

  @FunctionalInterface
  private interface ProbabilityStrategy {
    double calculate(List<Vertex> path, Vertex toVertex);
  }

  private static List<Vertex> mostProbablePath(
      final Vertex start,
      final double maximumMppLengthInMeters,
      final DirectedGraph<Vertex, Edge> graph,
      final PropertyMap<Vertex, Double> lengthPropertyMap,
      final ProbabilityStrategy probabilityStrategy) {
    final List<Vertex> path = new ArrayList<>();
    path.add(start);
    double pathLength = lengthPropertyMap.get(start);

    while (pathLength < maximumMppLengthInMeters) {
      final Iterator<Edge> edges = graph.getOutEdgeIterator(getLast(path));
      if (!edges.hasNext()) {
        break;
      }

      final SortedSet<Vertex> nextVerticesByAscendingProbability =
          new TreeSet<>(Comparator.comparing(w -> probabilityStrategy.calculate(path, w)));
      edges.forEachRemaining(e -> nextVerticesByAscendingProbability.add(graph.getTarget(e)));

      final Vertex nextVertex = nextVerticesByAscendingProbability.last();
      pathLength += lengthPropertyMap.get(nextVertex);
      path.add(nextVertex);
    }

    return path;
  }

  private static Predicate<Edge> filterBy(
      final DirectedGraph<Vertex, Edge> graph,
      final RangeBasedPropertyMap<Vertex, Boolean> booleanRangeBasedProperty) {
    return e -> checkBooleanRangedProperty(graph.getTarget(e), booleanRangeBasedProperty);
  }

  private static boolean checkBooleanRangedProperty(
      final Vertex v, final RangeBasedPropertyMap<Vertex, Boolean> propertyMap) {
    final List<RangeBasedProperty<Boolean>> ranges = propertyMap.get(v);
    return !ranges.isEmpty() && ranges.stream().allMatch(RangeBasedProperty::value);
  }

  private static ProbabilityStrategy probability(
      final RangeBasedPropertyMap<Vertex, FunctionalClass> functionalClassMap,
      final PropertyMap<Vertex, LineStringHolder<GeoCoordinate>> geometryMap) {
    return (current, next) ->
        0.5 * turnAngleProbability(getLast(current), next, geometryMap)
            + 0.5 * functionalClassProbability(getLast(current), next, functionalClassMap);
  }

  private static double turnAngleProbability(
      final Vertex current,
      final Vertex next,
      final PropertyMap<Vertex, LineStringHolder<GeoCoordinate>> geometryPropertyMap) {
    final List<GeoCoordinate> endOfCurrent =
        takeRight(geometryPropertyMap.get(current).getPoints(), 4);
    final List<GeoCoordinate> startOfNext = take(geometryPropertyMap.get(next).getPoints(), 4);

    final double deltaAngle = normalizeLongitude(heading(startOfNext) - heading(endOfCurrent));
    return 1.0 - abs(deltaAngle) / 180.0;
  }

  private static <T> List<T> take(final List<T> list, final int n) {
    return list.subList(0, min(list.size(), n));
  }

  private static <T> List<T> takeRight(final List<T> list, final int n) {
    return list.subList(max(list.size() - n, 0), list.size());
  }

  private static double heading(final List<GeoCoordinate> geometry) {
    return GeoCoordinates.getInstance().heading(geometry.get(0), getLast(geometry));
  }

  private static double functionalClassProbability(
      final Vertex current,
      final Vertex next,
      final RangeBasedPropertyMap<Vertex, FunctionalClass> functionalClass) {
    // Functional Class at the end (1.0) of the current link
    final int fcCurrent = functionalClass.get(current, 1.0).get().value().value();
    // Functional Class at the start (0.0) of the next link
    final int fcNext = functionalClass.get(next, 0.0).get().value().value();
    if (!validFunctionalClass(fcCurrent) || !validFunctionalClass(fcNext)) return 0.0;

    final int fcDelta = fcCurrent - fcNext;
    if (fcDelta <= -2) return 0.1;
    else if (fcDelta == -1) return 0.2;
    else if (fcDelta == 0) return 0.5;
    else if (fcDelta == 1) return 0.7;
    else return 1.0;
  }

  private static boolean validFunctionalClass(final int fc) {
    return fc >= 1 && fc <= 5;
  }

  private static <T> T getLast(final List<T> path) {
    return path.get(path.size() - 1);
  }
}
