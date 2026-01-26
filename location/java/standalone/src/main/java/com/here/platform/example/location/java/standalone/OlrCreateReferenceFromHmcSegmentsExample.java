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

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.compilation.heremapcontent.TopologyAttributeDescription;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.core.mapmatching.MatchResult;
import com.here.platform.location.core.mapmatching.OnRoad;
import com.here.platform.location.core.mapmatching.javadsl.MatchedPath;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.geospatial.HereMapContentReference;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import com.here.platform.location.integration.optimizedmap.mapmatching.javadsl.PathMatchers;
import com.here.platform.location.referencing.LinearLocation;
import com.here.platform.location.referencing.LocationReferenceCreator;
import com.here.platform.location.referencing.javadsl.LocationReferenceCreators;
import com.here.platform.location.referencing.olr.OlrPrettyPrinter;
import com.here.platform.location.tpeg2.XmlMarshallers;
import com.here.platform.location.tpeg2.javadsl.BinaryMarshallers;
import com.here.platform.location.tpeg2.olr.LinearLocationReference;
import com.here.platform.location.tpeg2.olr.OpenLRLocationReference;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This example shows how to take a path given as HERE Map Content references and create an OLR
 * reference from it.
 */
public final class OlrCreateReferenceFromHmcSegmentsExample {

  public static void main(final String[] args) throws FileNotFoundException {
    final List<GeoCoordinate> path =
        List.of(
            new GeoCoordinate(52.526323, 13.368411),
            new GeoCoordinate(52.526055, 13.367710),
            new GeoCoordinate(52.525940, 13.367385),
            new GeoCoordinate(52.525815, 13.366990),
            new GeoCoordinate(52.525677, 13.366529),
            new GeoCoordinate(52.525492, 13.365993),
            new GeoCoordinate(52.524971, 13.364546),
            new GeoCoordinate(52.524525, 13.363130),
            new GeoCoordinate(52.524420, 13.362815),
            new GeoCoordinate(52.524237, 13.362253),
            new GeoCoordinate(52.523935, 13.361275),
            new GeoCoordinate(52.523740, 13.360635),
            new GeoCoordinate(52.523589, 13.360122),
            new GeoCoordinate(52.523582, 13.358712),
            new GeoCoordinate(52.523705, 13.357395),
            new GeoCoordinate(52.523735, 13.356985),
            new GeoCoordinate(52.523660, 13.356810),
            new GeoCoordinate(52.523216, 13.356674),
            new GeoCoordinate(52.521860, 13.356260),
            new GeoCoordinate(52.520269, 13.355715),
            new GeoCoordinate(52.519508, 13.355407),
            new GeoCoordinate(52.519186, 13.355312),
            new GeoCoordinate(52.518710, 13.355185),
            new GeoCoordinate(52.518235, 13.355025),
            new GeoCoordinate(52.517683, 13.354852),
            new GeoCoordinate(52.517210, 13.354645),
            new GeoCoordinate(52.516859, 13.354292),
            new GeoCoordinate(52.516293, 13.353042),
            new GeoCoordinate(52.515802, 13.351872),
            new GeoCoordinate(52.515331, 13.351070),
            new GeoCoordinate(52.515166, 13.350226),
            new GeoCoordinate(52.515141, 13.349736),
            new GeoCoordinate(52.514928, 13.349263),
            new GeoCoordinate(52.514695, 13.349055),
            new GeoCoordinate(52.514412, 13.349066),
            new GeoCoordinate(52.514060, 13.349293),
            new GeoCoordinate(52.513897, 13.349654),
            new GeoCoordinate(52.513855, 13.349940),
            new GeoCoordinate(52.512909, 13.350431),
            new GeoCoordinate(52.511146, 13.350882),
            new GeoCoordinate(52.510010, 13.351145),
            new GeoCoordinate(52.509574, 13.350733),
            new GeoCoordinate(52.509402, 13.350056),
            new GeoCoordinate(52.508974, 13.349522),
            new GeoCoordinate(52.507996, 13.348989),
            new GeoCoordinate(52.507321, 13.348056),
            new GeoCoordinate(52.506886, 13.346848),
            new GeoCoordinate(52.506658, 13.345701),
            new GeoCoordinate(52.506535, 13.345075),
            new GeoCoordinate(52.506234, 13.343588),
            new GeoCoordinate(52.505904, 13.341960),
            new GeoCoordinate(52.505485, 13.341235),
            new GeoCoordinate(52.505235, 13.341035),
            new GeoCoordinate(52.505368, 13.340400),
            new GeoCoordinate(52.505356, 13.339351),
            new GeoCoordinate(52.505198, 13.338267),
            new GeoCoordinate(52.505286, 13.335972),
            new GeoCoordinate(52.505549, 13.333957),
            new GeoCoordinate(52.505935, 13.333118),
            new GeoCoordinate(52.506275, 13.332450),
            new GeoCoordinate(52.506569, 13.331741),
            new GeoCoordinate(52.507877, 13.332180));

    final BaseClient baseClient = BaseClientJava.instance();
    final OptimizedMapLayers optimizedMap =
        OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
            .usingBaseClient(baseClient)
            // Retain OLR attributes.
            // See
            // https://www.here.com/docs/bundle/location-library-developer-guide-java-scala/page/docs/high-level-v2_5.html#retain-only-required-attributes
            .withTopologyAttributes(
                TopologyAttributeDescription.RoadUsage(),
                TopologyAttributeDescription.FunctionalClass(),
                TopologyAttributeDescription.PhysicalAttribute(),
                TopologyAttributeDescription.SpecialTrafficAreaCategory())
            .newInstance()
            .version(7521L);

    try {
      final PropertyMap<HereMapContentReference, Vertex> hmcToVertex =
          new PropertyMaps(optimizedMap).hereMapContentReferenceToVertex();

      final List<Vertex> vertices = verticesFromPath(optimizedMap, path);

      final LinearLocation location = new LinearLocation(vertices, 0, 1);

      final LocationReferenceCreator<LinearLocation, LinearLocationReference> creator =
          new LocationReferenceCreators(optimizedMap).olrLinear();

      final LinearLocationReference reference = creator.create(location);

      // For debugging purposes there is a prettyPrint function.
      System.out.println(OlrPrettyPrinter.prettyPrint(reference));

      // This is how to serialize the reference to XML.
      XmlMarshallers.openLRLocationReference()
          .marshall(
              new OpenLRLocationReference("1.1", reference, Optional.empty()),
              new FileOutputStream("olr.xml"));

      // This is how to serialize the reference to binary.
      BinaryMarshallers.openLRLocationReference()
          .marshall(
              new OpenLRLocationReference("1.1", reference, Optional.empty()),
              new FileOutputStream("olr.bin"));
    } finally {
      baseClient.shutdown();
    }
  }

  public static List<Vertex> verticesFromPath(
      OptimizedMapLayers optimizedMap, List<GeoCoordinate> path) {
    MatchedPath<Vertex, List<Vertex>> matchedPath =
        new PathMatchers(optimizedMap)
            .<GeoCoordinate>carPathMatcherWithTransitions()
            .matchPath(path);
    List<MatchResult<Vertex>> results = matchedPath.results();
    List<com.here.platform.location.core.mapmatching.MatchedPath.Transition<List<Vertex>>>
        transitions = matchedPath.transitions();

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
