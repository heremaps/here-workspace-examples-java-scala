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

package com.here.platform.data.processing.example.java.pedestrian.protobuf;

import com.here.olp.util.quad.HereQuad;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Payload;
import com.here.platform.data.processing.java.blobstore.Retriever;
import com.here.platform.data.processing.java.catalog.partition.HereTile;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.compiler.CompileOut1To1Fn;
import com.here.platform.data.processing.java.compiler.RefTreeCompiler;
import com.here.platform.data.processing.java.compiler.reftree.CompileInFnWithRefs;
import com.here.platform.data.processing.java.compiler.reftree.Ref;
import com.here.platform.data.processing.java.compiler.reftree.RefTree;
import com.here.platform.data.processing.java.compiler.reftree.Subject;
import com.here.platform.data.processing.java.driver.Default;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.spark.partitioner.HashPartitioner;
import com.here.platform.data.processing.java.spark.partitioner.PartitionerOfKey;
import com.here.platform.pipeline.logging.java.ContextAwareLogger;
import com.here.platform.schema.data.processing.example.java.pedestrian.protobuf.model.v2.Model;
import com.here.schema.geometry.v2.GeometryOuterClass;
import com.here.schema.rib.v2.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Compiler
    implements RefTreeCompiler<Model.Polyline>,
        CompileInFnWithRefs<Model.Polyline>,
        CompileOut1To1Fn<Model.Polyline> {

  /** logger instance specific to this class */
  private ContextAwareLogger logger = new ContextAwareLogger(Compiler.class);

  /** retriever to get input data */
  private Retriever retriever;

  /** keep the configuration specific to this compiler */
  private CompilerConfig compilerConfig;

  /**
   * Constructor
   *
   * @param ctx A gate for user to the facilities provided by the framework.
   * @param cfg the compiler specific configuration
   */
  Compiler(DriverContext ctx, CompilerConfig cfg) {

    compilerConfig = cfg;
    retriever = ctx.inRetriever(Defs.In.RIB_CATALOG);
  }

  /**
   * The input partitioner
   *
   * @param parallelism the number of partitioner
   * @return the hash partitioner
   */
  @Override
  public Optional<PartitionerOfKey> inPartitioner(int parallelism) {
    return Optional.of(new HashPartitioner(parallelism));
  }

  /**
   * The output partitioner
   *
   * @param parallelism the number of partitioner
   * @return the same partitioner as the input
   */
  @Override
  public Optional<PartitionerOfKey> outPartitioner(int parallelism) {

    return inPartitioner(parallelism);
  }

  /**
   * Given a partition of the output layer and all the related topologies builds the output
   * partition.
   *
   * @param outKey Partition of the output layer
   * @param intermediate The topologies related to the output partition key.
   * @return the optional payload containing the results, if any.
   */
  @Override
  public Optional<Payload> compileOutFn(Key outKey, Iterable<Model.Polyline> intermediate) {

    if (!intermediate.iterator().hasNext()) {
      throw new IllegalStateException("Intermediate data cannot be empty in this compiler!");
    }

    // Stabilize based on id's and filter partition content
    TreeSet<Model.Polyline> set =
        StreamSupport.stream(intermediate.spliterator(), false)
            .collect(
                Collectors.toCollection(
                    () -> new TreeSet<>(Comparator.comparing(Model.Polyline::getIdentifier))));

    // Build the output partition protocol buffer and add the polylines
    Model.Geometry partition = Model.Geometry.newBuilder().addAllPolyLine(set).build();

    // Log results
    logger.info("processed data: {}", partition);

    // Return the result
    return Optional.of(new Payload(partition.toByteArray()));
  }

  /**
   * Reference structure. We define RoadLayer as subject layer and Topology as reference of
   * RoadLayer. So schematically it would look like RoadLayer -> Topology. In other words our output
   * tile with geometry depends on RoadLayer which in its turn depends on Topology. Knowing this
   * dependency model, incremental compiler will recompile geometry tile once either RoadLayer or
   * Topology that it depends on change.
   */
  @Override
  public RefTree refStructure() {
    return new RefTree()
        .addSubject(
            new Subject(new Pair<>(Defs.In.RIB_CATALOG, Defs.In.ROAD_LAYER))
                .addRef(
                    new Ref(topologyRef, new Pair<>(Defs.In.RIB_CATALOG, Defs.In.TOPOLOGY_LAYER))));
  }

  /**
   * Gets segment anchors of pedestrian access
   *
   * @param roadPartition the decoded road partition
   * @return a stream of pedestrian segment anchors
   */
  private Stream<Anchor.SegmentAnchor> getPedestrianSegmentAnchors(
      RoadAttributesPartitionOuterClass.RoadAttributesPartition roadPartition) {

    return roadPartition
        .getAccessibleByList()
        .stream()
        .filter(
            access ->
                access.getAppliesTo().getPedestrians()
                    && !access.getAppliesTo().getAutomobiles()
                    && !access.getAppliesTo().getBuses()
                    && !access.getAppliesTo().getCarpools()
                    && !access.getAppliesTo().getDeliveries()
                    && !access.getAppliesTo().getEmergencyVehicles()
                    && !access.getAppliesTo().getMotorcycles()
                    && !access.getAppliesTo().getTaxis()
                    && !access.getAppliesTo().getThroughTraffic()
                    && !access.getAppliesTo().getTrucks())
        .flatMap(pedestrian -> pedestrian.getSegmentAnchorIndexList().stream())
        .distinct()
        .map(roadPartition::getSegmentAnchor);
  }

  /**
   * Given RoadLayer partition, returns all partitions that are referenced by it
   *
   * @param src partition of the RoadLayer
   * @return All partitions that are referenced by RoadLayer
   */
  @Override
  public Map<String, Set<Key>> resolveFn(Pair<Key, Meta> src) {

    try {

      // Read subject partition
      RoadAttributesPartitionOuterClass.RoadAttributesPartition roadPartition =
          RoadAttributesPartitionOuterClass.RoadAttributesPartition.parseFrom(
              retriever.getPayload(src.getKey(), src.getValue()).content());

      // Get segment anchors
      Stream<Anchor.SegmentAnchor> segmentAnchors = getPedestrianSegmentAnchors(roadPartition);

      // Get the oriented references
      Stream<Anchor.SegmentAnchor.OrientedSegmentReference> orientedSegmentReferences =
          segmentAnchors.flatMap(
              segmentAnchor -> segmentAnchor.getOrientedSegmentRefList().stream());

      // Get the referred topology partitions
      Set<Key> topologyKeys =
          orientedSegmentReferences
              .map(orientedReference -> getTopologyKey(orientedReference.getSegmentRef()))
              .collect(Collectors.toSet());

      // Log the references
      logger.info("references: {}", topologyKeys);

      // Return the references
      return Collections.singletonMap(topologyRef, topologyKeys);

    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Get the topology partition key based from a reference
   *
   * @param reference the reference object
   * @return the referenced topology partition key
   */
  private Key getTopologyKey(Common.Reference reference) {

    return new Key(
        Defs.In.RIB_CATALOG,
        Defs.In.TOPOLOGY_LAYER,
        new HereTile(Long.parseLong(reference.getPartitionName())));
  }

  /**
   * Gets a map segmentId -> segments for segments fast look-up
   *
   * @param topologyPartitions the topology partitions to decode
   * @return returns a segmentId -> polyline
   */
  private Map<String, TopologyGeometry.Segment> getSegmentMap(Map<Key, Meta> topologyPartitions) {

    // Get topologies map
    return topologyPartitions
        .entrySet()
        .stream()
        .flatMap(
            topologyPartition -> {
              TopologyGeometryPartitionOuterClass.TopologyGeometryPartition partition;

              try {
                partition =
                    TopologyGeometryPartitionOuterClass.TopologyGeometryPartition.parseFrom(
                        retriever
                            .getPayload(topologyPartition.getKey(), topologyPartition.getValue())
                            .content());

              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }

              return partition.getSegmentList().stream();
            })
        .collect(Collectors.toMap(TopologyGeometry.Segment::getIdentifier, Function.identity()));
  }

  /**
   * Given a RoadLayer partition and its references in TopologyLayer returns output key and the
   * intermediate data required to compile it.
   *
   * @param roadMeta Partition on the RoadLayer
   * @param refs TopologyLayer partition references.
   * @return output key for geometry and intermediate data required to compile it
   */
  @Override
  public Iterable<Pair<Key, Model.Polyline>> compileInFn(
      Pair<Key, Meta> roadMeta, Map<Key, Meta> refs) {

    logger.info(
        "processing references: {}",
        () ->
            refs.entrySet()
                .stream()
                .map(pair -> pair.getKey().toString())
                .collect(Collectors.joining(", ")));

    if (refs.isEmpty()) return Collections.emptyList();

    try {

      // Get intermediate data map
      Map<String, TopologyGeometry.Segment> polyLines = getSegmentMap(refs);

      // Read subject partition
      RoadAttributesPartitionOuterClass.RoadAttributesPartition roadPartition =
          RoadAttributesPartitionOuterClass.RoadAttributesPartition.parseFrom(
              retriever.getPayload(roadMeta.getKey(), roadMeta.getValue()).content());

      // Get pedestrian intermediate data
      Stream<Model.Polyline> intermediateDataStream =
          getPedestrianSegmentAnchors(roadPartition)
              .flatMap(
                  segmentAnchor ->
                      segmentAnchor
                          .getOrientedSegmentRefList()
                          .stream()
                          .map(orientedRef -> getModelPolyline(orientedRef, polyLines)));

      return intermediateDataStream
          .map(intermediateData -> new Pair<>(getOutputKey(intermediateData), intermediateData))
          .collect(Collectors.toList());

    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Gets the compiler model polyline
   *
   * @param reference the RIB reference
   * @param polyLines the map of polilines to look-up
   * @return the compiler model polyline
   */
  private Model.Polyline getModelPolyline(
      Anchor.SegmentAnchor.OrientedSegmentReference reference,
      Map<String, TopologyGeometry.Segment> polyLines) {

    String identifier = reference.getSegmentRef().getIdentifier();
    TopologyGeometry.Segment topology = polyLines.get(identifier);

    if (topology == null)
      throw new NoSuchElementException("Topology not found for the identifier: " + identifier);

    GeometryOuterClass.LineString pointList =
        GeometryOuterClass.LineString.newBuilder()
            .addAllPoint(
                topology
                    .getGeometry()
                    .getPointList()
                    .stream()
                    .map(
                        point ->
                            GeometryOuterClass.Point.newBuilder()
                                .setLatitude(point.getLatitude())
                                .setLongitude(point.getLongitude())
                                .build())
                    .collect(Collectors.toList()))
            .build();

    return Model.Polyline.newBuilder().setIdentifier(identifier).setLineString(pointList).build();
  }

  /**
   * Get the output key of an intermediate data object
   *
   * <p>The intermediate object is assigned to the output layer partition containing its first
   * point. The level of the outut partition is set in the compiler configuration.
   *
   * @param intermediateData the intermediate data object
   * @return the output partition key
   */
  private Key getOutputKey(Model.Polyline intermediateData) {

    double latitude = intermediateData.getLineString().getPointList().get(0).getLatitude();
    double longitude = intermediateData.getLineString().getPointList().get(0).getLongitude();

    return new Key(
        Default.OutCatalogId(),
        Defs.Out.ROAD_GEOMETRY_LAYER,
        new HereTile(new HereQuad(latitude, longitude, compilerConfig.getOutputLevel())));
  }

  /** Reference names */
  private String topologyRef = "topology";

  @Override
  public Set<String> outLayers() {

    return Defs.LayerDefs.outLayers;
  }

  @Override
  public Map<String, Set<String>> inLayers() {

    return Defs.LayerDefs.inLayers;
  }
}
