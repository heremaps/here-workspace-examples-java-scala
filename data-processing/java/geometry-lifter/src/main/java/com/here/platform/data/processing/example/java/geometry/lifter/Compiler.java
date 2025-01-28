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

package com.here.platform.data.processing.example.java.geometry.lifter;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.olp.util.quad.HereQuad;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Payload;
import com.here.platform.data.processing.java.blobstore.Retriever;
import com.here.platform.data.processing.java.catalog.partition.HereTile;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.compiler.CompileOut1To1Fn;
import com.here.platform.data.processing.java.compiler.DirectMToNCompiler;
import com.here.platform.data.processing.java.driver.Default;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.spark.partitioner.LocalityAwarePartitioner;
import com.here.platform.data.processing.java.spark.partitioner.PartitionerOfKey;
import com.here.platform.pipeline.logging.java.ContextAwareLogger;
import com.here.schema.rib.v2.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Compiler
    implements DirectMToNCompiler<IntermediateData>, CompileOut1To1Fn<IntermediateData> {

  /** Keep the configuration specific to this compiler */
  private CompilerConfig compilerConfig;

  /** Logger instance for this class */
  private ContextAwareLogger logger = new ContextAwareLogger(Compiler.class);

  /** Retriever to get input data */
  private Retriever retriever;

  /**
   * Constructor
   *
   * @param ctx Context for using the platform.
   * @param cfg Contains batch processor specific configuration.
   */
  Compiler(DriverContext ctx, CompilerConfig cfg) {
    compilerConfig = cfg;
    retriever = ctx.inRetriever(Defs.In.RIB_CATALOG);
  }

  /**
   * The input partitioner
   *
   * @param parallelism The parallelism of the partitioner
   * @return The input optional partitioner with the given parallelism
   */
  @Override
  public Optional<PartitionerOfKey> inPartitioner(int parallelism) {
    return Optional.of(
        new LocalityAwarePartitioner(parallelism, compilerConfig.getPartitionerLevel()));
  }

  /**
   * The output partitioner
   *
   * @param parallelism The parallelism of the partitioner
   * @return The output optional partitioner with the given parallelism
   */
  @Override
  public Optional<PartitionerOfKey> outPartitioner(int parallelism) {
    return inPartitioner(parallelism);
  }

  /**
   * Calculates which output partitions the given input partition impacts.
   *
   * @param inKey The input partition being mapped
   * @return The output partitions to which the given input partition maps
   */
  @Override
  public java.lang.Iterable<Key> mappingFn(Key inKey) {
    HereTile hereTile = getAncestorTile(inKey.partition().toString());
    return Collections.singletonList(
        new Key(Default.OutCatalogId(), Defs.Out.LIFTED_TOPOLOGY_GEOMETRY_LAYER, hereTile));
  }

  /**
   * @return Layers of the input catalogs that should be queried and provided to the compiler,
   *     grouped by input catalog and identified by catalog ID and layer name.
   */
  @Override
  public Map<String, Set<String>> inLayers() {
    return Defs.LayerDefs.inLayers;
  }

  /**
   * @return The output layers the compiler intends to produce. As there is only one output catalog,
   *     it is not necessary to specify its identifier
   */
  @Override
  public Set<String> outLayers() {
    return Defs.LayerDefs.outLayers;
  }

  /**
   * Given a TopologyLayer partition returns output key and the intermediate data required to
   * compile it.
   *
   * @param topologyMeta Partition on the RoadLayer
   * @return output key for geometry and intermediate data required to compile it
   */
  @Override
  public IntermediateData compileInFn(Pair<Key, Meta> topologyMeta) {
    logger.debug("map input partition {} to intermediate data", topologyMeta::getKey);
    return new IntermediateData(topologyMeta.getKey(), topologyMeta.getValue());
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
  public Optional<Payload> compileOutFn(Key outKey, Iterable<IntermediateData> intermediate) {
    TopologyGeometryPartitionOuterClass.TopologyGeometryPartition.Builder builder =
        TopologyGeometryPartitionOuterClass.TopologyGeometryPartition.newBuilder();
    String partitionName = outKey.partition().toString();
    builder.setPartitionName(partitionName);

    StreamSupport.stream(intermediate.spliterator(), false)
        .map(data -> retriever.getPayload(data.getKey(), data.getMeta()))
        .map(this::getTopologyGeometryPartition)
        .forEach(
            partition -> {
              // lift segmentRefs in nodes
              List<TopologyGeometry.Node> nodes = liftNodes(partition.getNodeList());

              // lift startNodeRef and endNodeRef in segments
              List<TopologyGeometry.Segment> segments = liftSegments(partition.getSegmentList());

              builder.addAllNode(nodes);
              builder.addAllSegment(segments);
            });
    return Optional.of(new Payload(builder.build().toByteArray()));
  }

  /**
   * Parses payload to object
   *
   * @param payload The payload to be converted.
   * @return partition of topology-geometry
   */
  private TopologyGeometryPartitionOuterClass.TopologyGeometryPartition
      getTopologyGeometryPartition(Payload payload) {
    try {
      return TopologyGeometryPartitionOuterClass.TopologyGeometryPartition.parseFrom(
          payload.content());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Update partition name in segmentRef for every node
   *
   * @param nodes the input list of nodes
   * @return the list of nodes with updated partition name
   */
  private List<TopologyGeometry.Node> liftNodes(List<TopologyGeometry.Node> nodes) {
    return nodes
        .stream()
        .map(
            node -> {
              List<Common.Reference> references =
                  node.getSegmentRefList()
                      .stream()
                      .map(this::liftReference)
                      .collect(Collectors.toList());
              TopologyGeometry.Node.Builder builder = node.toBuilder().clearSegmentRef();
              builder.addAllSegmentRef(references);
              return builder.build();
            })
        .collect(Collectors.toList());
  }

  /**
   * Update partition name in startNodeRef and endNodeRef for every segment
   *
   * @param segments the input list of segments
   * @return the list of segments with updated partition name
   */
  private List<TopologyGeometry.Segment> liftSegments(List<TopologyGeometry.Segment> segments) {
    return segments
        .stream()
        .map(
            segment -> {
              Common.Reference startRef = liftReference(segment.getStartNodeRef());
              Common.Reference endRef = liftReference(segment.getEndNodeRef());
              TopologyGeometry.Segment.Builder builder = segment.toBuilder();
              builder.setStartNodeRef(startRef).setEndNodeRef(endRef);
              return builder.build();
            })
        .collect(Collectors.toList());
  }

  /**
   * Update partition name in reference
   *
   * @param reference the reference to be lifted
   * @return a reference with the tile lifted
   */
  private Common.Reference liftReference(Common.Reference reference) {
    HereTile refTile = getAncestorTile(reference.getPartitionName());
    return reference.toBuilder().setPartitionName(refTile.toString()).build();
  }

  /**
   * Get ancestor tile at the configured zoom level
   *
   * @param key quad key of current tile
   * @return tile at the configured zoom level
   */
  private HereTile getAncestorTile(String key) {
    long longKey = Long.parseLong(key);
    return new HereTile(new HereQuad(longKey).getAncestor(compilerConfig.getOutputLevel()));
  }
}
