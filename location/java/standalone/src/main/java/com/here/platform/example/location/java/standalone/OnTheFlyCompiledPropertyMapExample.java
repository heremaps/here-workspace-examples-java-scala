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

import com.google.protobuf.CodedInputStream;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.data.client.v2.api.javadsl.versioned.VersionedHereTileLayerReader;
import com.here.platform.data.client.v2.caching.javadsl.versioned.VersionedLayerReaderConfiguration;
import com.here.platform.data.client.v2.caching.javadsl.versioned.VersionedLayerReadersConfiguration;
import com.here.platform.data.client.v2.main.javadsl.DataClient;
import com.here.platform.example.location.java.standalone.OnTheFlyCompiledPropertyMapExample.DirectedSegmentId;
import com.here.platform.example.location.java.standalone.OnTheFlyCompiledPropertyMapExample.OnTheFlyPartition;
import com.here.platform.example.location.java.standalone.OnTheFlyCompiledPropertyMapExample.SpeedLimitWithColor;
import com.here.platform.example.location.java.standalone.OnTheFlyCompiledPropertyMapExample.VertexWithProperty;
import com.here.platform.location.compilation.heremapcontent.SegmentAttribute;
import com.here.platform.location.compilation.heremapcontent.javadsl.AttributeAccessor;
import com.here.platform.location.compilation.heremapcontent.javadsl.AttributeAccessors;
import com.here.platform.location.core.geospatial.Coordinate2D;
import com.here.platform.location.core.geospatial.ElementProjection;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.core.geospatial.SinusoidalProjection$;
import com.here.platform.location.core.geospatial.javadsl.LineString;
import com.here.platform.location.core.geospatial.javadsl.LineStringHolder;
import com.here.platform.location.core.geospatial.javadsl.LineStrings;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.core.graph.javadsl.RangeBasedProperty;
import com.here.platform.location.core.graph.javadsl.RangeBasedPropertyMap;
import com.here.platform.location.inmemory.graph.Direction;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.inmemory.graph.Vertices;
import com.here.platform.location.integration.heremapcontent.HereMapContent;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.geospatial.HereMapContentReference;
import com.here.platform.location.integration.optimizedmap.geospatial.SegmentId;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import com.here.platform.location.io.javadsl.Color;
import com.here.platform.location.io.javadsl.geojson.FeatureCollection;
import com.here.platform.location.io.javadsl.geojson.SimpleStyleProperties;
import com.here.schema.rib.v2.anchor.SegmentAnchor;
import com.here.schema.rib.v2.common.RelativeDirection;
import com.here.schema.rib.v2.common_attributes.SpeedLimitAttribute;
import com.here.schema.rib.v2.topology_attributes_partition.TopologyAttributesPartition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** An example that shows how to compile Topology attributes from HERE Map Content on the fly
  * and use them as properties of vertices from the `Optimized Map for Location Library`.
  */
 public class OnTheFlyCompiledPropertyMapExample {
  record DirectedSegmentId(SegmentId segmentId, Direction direction) {}

  record SpeedLimitWithColor(int speedLimit, Color color) {}

  record VertexWithProperty<T>(Vertex vertex, List<RangeBasedProperty<T>> rangeBasedProperties) {}

  static class RangeBasedPropertyConverter {
    public static <T> Map<DirectedSegmentId, List<RangeBasedProperty<T>>> convert(
         Iterable<SegmentAttribute<T>> it) {
      return StreamSupport.stream(it.spliterator(), false)
              .filter(sa -> sa.anchor().orientedSegmentRef().length() == 1)
              .flatMap(sa -> {
                var anchor = sa.anchor();
                var orientedSegmentRef = anchor.orientedSegmentRef().apply(0);

                RangeBasedProperty<T> forward =
                        new com.here.platform.location.core.graph.RangeBasedProperty<>(
                                anchor.firstSegmentStartOffset().getOrElse(() -> 0.0),
                                anchor.lastSegmentEndOffset().getOrElse(() -> 1.0),
                                sa.value());

                RangeBasedProperty<T> backward =
                        new com.here.platform.location.core.graph.RangeBasedProperty<>(
                                1.0 - anchor.lastSegmentEndOffset().getOrElse(() -> 1.0),
                                1.0 - anchor.firstSegmentStartOffset().getOrElse(() -> 0.0),
                                sa.value());

                return Stream.concat(
                        isForward(anchor.attributeOrientation(), orientedSegmentRef)
                                ? Stream.of(Map.entry(
                                toDirectedSegmentId(orientedSegmentRef,
                                        com.here.platform.location.inmemory.graph.javadsl.Direction.FORWARD),
                                forward))
                                : Stream.empty(),
                        isBackward(anchor.attributeOrientation(), orientedSegmentRef)
                                ? Stream.of(Map.entry(
                                toDirectedSegmentId(orientedSegmentRef,
                                        com.here.platform.location.inmemory.graph.javadsl.Direction.BACKWARD),
                                backward))
                                : Stream.empty());
              })
              .collect(Collectors.groupingBy(
                      Map.Entry::getKey,
                      Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static boolean isForward(
         RelativeDirection attributeOrientation,
         SegmentAnchor.OrientedSegmentReference orientedSegmentRef) {
      boolean inverted = orientedSegmentRef.inverted();
      return attributeOrientation.isBoth()
          || (attributeOrientation.isForward() && !inverted)
          || (attributeOrientation.isBackward() && inverted);
    }

    private static boolean isBackward(
        RelativeDirection attributeOrientation,
        SegmentAnchor.OrientedSegmentReference orientedSegmentRef) {
      boolean inverted = orientedSegmentRef.inverted();
      return attributeOrientation.isBoth()
          || (attributeOrientation.isForward() && inverted)
          || (attributeOrientation.isBackward() && !inverted);
    }

    private static DirectedSegmentId toDirectedSegmentId(
        SegmentAnchor.OrientedSegmentReference orientedSegmentRef, Direction direction) {
      return new DirectedSegmentId(
          new SegmentId(orientedSegmentRef.segmentRef().get().identifier()), direction);
    }
  }

  // You can add here the other fields that need to be compiled
  record OnTheFlyPartition(Map<DirectedSegmentId, List<RangeBasedProperty<SpeedLimitWithColor>>> speedLimitWithColor) {
    private static final AttributeAccessor<TopologyAttributesPartition, SpeedLimitWithColor>
        speedLimitWithColorAccessor =
            AttributeAccessors.forHereMapContentSegmentAnchor(
                    TopologyAttributesPartition::speedLimit,
                    SpeedLimitAttribute::value)
                .map(
                    s -> new SpeedLimitWithColor(s, Color.hsb(Math.min(Math.max(s, 0), 60), 1, 1)));

    public static OnTheFlyPartition fromHereMapContent(
         TopologyAttributesPartition topologyAttributesPartition) {
      return new OnTheFlyPartition(
          RangeBasedPropertyConverter.convert(
              speedLimitWithColorAccessor.apply(topologyAttributesPartition)));
    }
  }

  static class OnTheFlyRangeBasedPropertyMap<P, T> implements RangeBasedPropertyMap<Vertex, T> {

    private final VersionedHereTileLayerReader<P> reader;
    private final long version;
    private final PropertyMap<Vertex, HereMapContentReference> vertexToHereMapContentReference;
    private final Function<P, Map<DirectedSegmentId, List<RangeBasedProperty<T>>>> accessor;

    public OnTheFlyRangeBasedPropertyMap(
         VersionedHereTileLayerReader<P> reader,
         long version,
         PropertyMap<Vertex, HereMapContentReference> vertexToHereMapContentReference,
         Function<P, Map<DirectedSegmentId, List<RangeBasedProperty<T>>>> accessor) {
      this.reader = reader;
      this.version = version;
      this.vertexToHereMapContentReference = vertexToHereMapContentReference;
      this.accessor = accessor;
    }

    @Override
    public List<RangeBasedProperty<T>> get(Vertex key) {
      var hmcRef = vertexToHereMapContentReference.get(key);
      return reader
          .read(version, Long.parseLong(hmcRef.partitionId()))
          .flatMap(
              p ->
                  Optional.ofNullable(
                      accessor
                          .apply(p)
                          .get(
                              new DirectedSegmentId(
                                  new SegmentId(hmcRef.segmentId()), hmcRef.direction()))))
          .orElse(List.of());
    }

    @Override
    public boolean containsKey(Vertex key) {
      return vertexToHereMapContentReference.containsKey(key);
    }

    @Override
    public Optional<RangeBasedProperty<T>> get(Vertex key, double offset) {
      var ranges = get(key);
      var from = 0;
      var to = ranges.size() - 1;
      while (from <= to) {
        var at = from + to >>> 1;
        var candidate = ranges.get(at);
        if (candidate.start() > offset) to = at - 1;
        else if (candidate.end() < offset || (at < to && ranges.get(at + 1).start() <= offset)) from = at + 1;
        else return Optional.of(candidate);
      }
      return Optional.empty();
    }
  }

  public static void main(String[] args) throws Exception {

    var om4llVersion = 8181L;
    var brandenburgerTor = new GeoCoordinate(52.516268, 13.377700);
    var radiusInMeters = 1000.0;

    var baseClient = BaseClientJava.instance();

    try {
      var optimizedMapCatalog =
          OptimizedMapCatalog.from(OptimizedMap.v2.HRN).usingBaseClient(baseClient).newInstance();

      var optimizedMap = optimizedMapCatalog.version(om4llVersion);

      var hmcVersion =
          optimizedMapCatalog
              .versionInfo()
              .resolveDependencyVersion(om4llVersion, HereMapContent.v2.HRN)
              .orElseThrow();

      var hmcCatalog =
          new DataClient(baseClient)
              .catalogBuilder(HereMapContent.v2.HRN)
              .withVersionedLayerReadersConfiguration(
                  new VersionedLayerReadersConfiguration()
                      .withLayer(
                          "topology-attributes",
                          new VersionedLayerReaderConfiguration<>(
                              is ->
                                  OnTheFlyPartition.fromHereMapContent(
                                      TopologyAttributesPartition.parseFrom(
                                          CodedInputStream.newInstance(is))))))
              .build();

      var vertexToHereMapContentReference =
          new PropertyMaps(optimizedMap).vertexToHereMapContentReference();

      var speedLimitWithColor = new OnTheFlyRangeBasedPropertyMap<>(
              hmcCatalog.getVersionedLayers().getVersionedHereTileLayer("topology-attributes").getReader(),
              hmcVersion,
              vertexToHereMapContentReference,
              OnTheFlyPartition::speedLimitWithColor);

      var proximitySearch = new ProximitySearches(optimizedMap).vertices();

      var verticesInRange =
          StreamSupport.stream(
                  proximitySearch.search(brandenburgerTor, radiusInMeters).spliterator(), false)
              .map(ElementProjection::element)
              .toList();

      var verticesWithProperties = verticesInRange.stream()
              .map(vertex -> new VertexWithProperty<>(vertex, speedLimitWithColor.get(vertex)))
              .toList();

      var geometryPropertyMap = new PropertyMaps(optimizedMap).geometry();

      serializeToGeoJson(verticesWithProperties, geometryPropertyMap);

    } finally {
      baseClient.shutdown();
    }
  }

  private static void serializeToGeoJson(Iterable<VertexWithProperty<SpeedLimitWithColor>> it, PropertyMap<Vertex, LineStringHolder<GeoCoordinate>> geometry) throws IOException {
    var featureCollection = new FeatureCollection();
    for (var verticesWithColor: it) {
      var vertex = verticesWithColor.vertex();
      for (var rangeBasedProperty : verticesWithColor.rangeBasedProperties()) {
        var speedLimitWithColor = rangeBasedProperty.value();
        LineStrings<LineStringHolder<GeoCoordinate>, GeoCoordinate> lineStrings = LineStrings.getInstance();
        var partialLineRanges = List.of(new scala.Tuple2<>(rangeBasedProperty.start(), rangeBasedProperty.end()));
        var partialLine = lineStrings.cut(geometry.get(vertex), partialLineRanges).get(0);
        var isForward = Vertices.directionOf(vertex) == com.here.platform.location.inmemory.graph.javadsl.Direction.FORWARD;
        var shiftedPartialLine = shiftNorthWest(partialLine, isForward ? 2 : -2);
        featureCollection.lineString(
                shiftedPartialLine,
                new SimpleStyleProperties().stroke(speedLimitWithColor.color()).add("speedLimit", Integer.toString(speedLimitWithColor.speedLimit())));
      }
    }
    var path = exampleJsonFileFor(OnTheFlyCompiledPropertyMapExample.class);
    try (OutputStream os = new FileOutputStream(path)) {
      featureCollection.writePretty(os);
    }
    System.out.printf("\nA GeoJson representation of the result is available in %s\n", path);
  }

  private static LineStringHolder<GeoCoordinate> shiftNorthWest(LineStringHolder<GeoCoordinate> ls, double distance) {
    var projection = SinusoidalProjection$.MODULE$;
    return new LineString<>(ls.getPoints().stream().map((GeoCoordinate pgc) -> {
      var projected = projection.to(pgc, pgc, GeoCoordinate.GeoCoordinatesOps$.MODULE$, GeoCoordinate.GeoCoordinatesOps$.MODULE$);
      return projection.from(pgc, new Coordinate2D(projected.x() + distance, projected.y() + distance), GeoCoordinate.GeoCoordinatesOps$.MODULE$, Coordinate2D.Coordinate2DOps$.MODULE$);
    }).toList());
  }

  private static File exampleJsonFileFor(Class<?> clazz) {
    File outputDir = new File(System.getProperty("java.io.tmpdir"), "example_output");
    outputDir.mkdir();
    return new File(outputDir, clazz.getCanonicalName() + ".json");
  }
}
