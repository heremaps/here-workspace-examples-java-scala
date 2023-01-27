/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

package com.here.platform.data.processing.example.java.feedback;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.data.processing.example.java.feedback.Defs.In;
import com.here.platform.data.processing.example.java.feedback.Defs.LayerDefs;
import com.here.platform.data.processing.example.java.feedback.Defs.Out;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Payload;
import com.here.platform.data.processing.java.blobstore.Retriever;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.compiler.CompileOut1To1Fn;
import com.here.platform.data.processing.java.compiler.DirectMToNCompiler;
import com.here.platform.data.processing.java.driver.Default;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.spark.partitioner.NameHashPartitioner;
import com.here.platform.data.processing.java.spark.partitioner.PartitionerOfKey;
import com.here.schema.rib.v2.TopologyGeometry.Node;
import com.here.schema.rib.v2.TopologyGeometryPartitionOuterClass.TopologyGeometryPartition;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Shows how to use the output of the previous compilation as feedback input to count the number of
 * times the node cardinalities have changed for each partition.
 *
 * <p>In this example output keys depend only on input keys, so the compiler most suited for this is
 * one of the <a
 * href="https://developer.here.com/documentation/data-processing-library/dev_guide/topics/functional-patterns.html#direct-1n-and-mn-compilers">Direct
 * compilers</a>. The Direct1ToNCompiler does not fit in this case, because in compileOutFn, we may
 * have two intermediate data instances for partition - one for input data and one for the feedback
 * data (if available). Consequently, we use the {@link DirectMToNCompiler}
 *
 * <p>Each output partition should be written to one output layer, once, so we use {@link
 * CompileOut1To1Fn}
 */
public class Compiler
    implements DirectMToNCompiler<IntermediateData>, CompileOut1To1Fn<IntermediateData> {

  /** retriever to get input data */
  private Retriever retriever;

  /** retriever to get feedback data */
  private Retriever feedBackRetriever;

  /**
   * Constructor
   *
   * @param ctx A gate for user to the features provided by the library. Here it is used to get a
   *     {@link Retriever} for the single input catalog and one for the feedback catalog that are
   *     created using the input catalog configuration provided.
   */
  Compiler(DriverContext ctx) {
    retriever = ctx.inRetriever(In.RIB_CATALOG);
    feedBackRetriever = ctx.feedbackRetriever();
  }

  /**
   * The input partitioner. Input partitions and feedback partitions (if available), have the same
   * partition name but different catalog ID and layer ID. <a
   * href="https://developer.here.com/documentation/data-processing-library/dev_guide/topics/partitioners-spark-additions.html">Overview
   * of available partitioners</a>. A partitioner that uses only the partition name is the {@link
   * NameHashPartitioner}.
   *
   * @param parallelism The total number of partitions.
   * @return The input partitioner.
   */
  @Override
  public Optional<PartitionerOfKey> inPartitioner(int parallelism) {
    return Optional.of(new NameHashPartitioner(parallelism));
  }

  /**
   * The output partitioner. Output partition names are equal to the input partition names (see
   * {@link #mappingFn(Key)} so we use the same partitioner as in {@link #inPartitioner(int)}; this
   * prevents data shuffling.
   *
   * @param parallelism The number of partitions.
   * @return The same partitioner as the {@link #inPartitioner(int)}.
   */
  @Override
  public Optional<PartitionerOfKey> outPartitioner(int parallelism) {
    return inPartitioner(parallelism);
  }

  /**
   * The {@link DirectMToNCompiler}'s output keys depend only on input keys. For each input
   * partition we expect to have an output partition with the same partition name (say, a number
   * like "410374") but with output catalog ID and layer ID.
   *
   * <p>Consider that the current function processes the input from *all* input layers, so that
   * inKey.layer() may return either {@link In#TOPOLOGY_LAYER} or {@link In#NODECARDINALITY_LAYER}.
   *
   * @return An {@link Iterable} with output partition.
   */
  @Override
  public java.lang.Iterable<Key> mappingFn(Key inKey) {
    Key outKey = new Key(Default.OutCatalogId(), Out.NODECARDINALITY_LAYER, inKey.partition());
    return Collections.singletonList(outKey);
  }

  /**
   * @return layers of the input catalogs that should be queried and provided to the compiler,
   *     grouped by input catalog and identified by catalog id and layer name.
   */
  @Override
  public Map<String, Set<String>> inLayers() {

    return Defs.LayerDefs.inLayers;
  }

  /**
   * @return the output layers compiler intends to populate. As there is only one output catalog, it
   *     is not necessary to specify its identifier
   */
  @Override
  public Set<String> outLayers() {

    return LayerDefs.outLayers;
  }

  /**
   * Transforms each input {@link Pair} to a more convenient {@link IntermediateData}, that contains
   * partition data. This data is then processed by {@link #compileOutFn(Key, Iterable)}.
   *
   * <p>Refer to `here.platform.data-processing.executors.compilein` part in <a
   * href="https://developer.here.com/documentation/data-processing-library/dev_guide/topics/configuration.html">configuration
   * page</a> to check configuration options available for this function
   *
   * @param in The input partition to process.
   * @return The value of intermediate data.
   */
  @Override
  public IntermediateData compileInFn(Pair<Key, Meta> in) {
    return new IntermediateData(in.getKey(), in.getValue());
  }

  /**
   * The output depends on the input catalog partitions and on the previous compilation results. We
   * generate a partition containing the node cardinalities, we compare it with the previously
   * generated one, if available. If the content has changed, we also increment the run counter.
   *
   * <p>Refer to `here.platform.data-processing.executors.compileout` part in <a
   * href="https://developer.here.com/documentation/data-processing-library/dev_guide/topics/configuration.html">configuration
   * page</a> to check configuration options available for this function
   *
   * @param outKey The key of the output partition to generate.
   * @param intermediate Non-empty {@link Iterable} of intermediate data as provided by compileInFn
   *     which can have at most two entries: one for input layer and one for feedback layer.
   * @return {@link Optional} of {@link com.here.platform.data.processing.blobstore.Payload}: - same
   *     as feedback data if input data not changed - new with updated counter if changes present or
   *     feedback input is empty - {@link Optional#EMPTY} if input and feedback data not present.
   */
  @Override
  public Optional<Payload> compileOutFn(Key outKey, Iterable<IntermediateData> intermediate) {
    Optional<IntermediateData> inLayerEntry = getByLayerId(intermediate, In.TOPOLOGY_LAYER);
    Optional<IntermediateData> feedbackLayerEntry =
        getByLayerId(intermediate, In.NODECARDINALITY_LAYER);

    Optional<Map<String, Integer>> optCardinalityMap =
        inLayerEntry.map(
            tgData ->
                createNodeCardinalityMap(retriever.getPayload(tgData.key, tgData.meta).content()));

    Optional<byte[]> optFeedbackBin =
        feedbackLayerEntry.map(
            feedback -> feedBackRetriever.getPayload(feedback.key, feedback.meta).content());

    // obtain cardinality map from input partition
    Optional<NodeCardinalityRunCounter> optFeedback =
        optFeedbackBin.map(NodeCardinalityRunCounter::fromByteArray);

    boolean isHashMatch =
        optFeedback
            .map(NodeCardinalityRunCounter::hash)
            .equals(optCardinalityMap.map(Map::hashCode));

    Optional<byte[]> binaryOutput;

    if (isHashMatch || !optCardinalityMap.isPresent()) {
      // no changes, or geometry-topology is absent
      binaryOutput = optFeedbackBin;
    } else if (!optFeedback.isPresent()) {
      // empty feedback layer, first run
      binaryOutput =
          optCardinalityMap.map(
              card -> new NodeCardinalityRunCounter(card, 1, card.hashCode()).toByteArray());
    } else {
      // input partition changed since last run
      binaryOutput =
          optCardinalityMap.flatMap(
              card ->
                  optFeedback.map(
                      feed ->
                          new NodeCardinalityRunCounter(card, feed.count() + 1, card.hashCode())
                              .toByteArray()));
    }

    return binaryOutput.map(Payload::new);
  }

  /**
   * Looks for the intermediate data entry related to a provided layer name
   *
   * @param intermediateData to seek in
   * @param layerName required layer ID
   * @return found entry or {@link Optional#EMPTY} if such entry not available
   */
  private Optional<IntermediateData> getByLayerId(
      Iterable<IntermediateData> intermediateData, String layerName) {
    return StreamSupport.stream(intermediateData.spliterator(), Boolean.FALSE)
        .filter(data -> data.key.layer().equals(layerName))
        // we expect that maximum one entry in each layer - one from data and one from feedback
        // layer
        .findFirst();
  }

  /**
   * Creates a map that contains all nodes and their cardinality from a given serialized {@link
   * TopologyGeometryPartition}. The cardinalities are stored in a sorted map to make sure the
   * output data is deterministic
   *
   * @param topologyGeometryBin to process
   * @return prepared map
   */
  private Map<String, Integer> createNodeCardinalityMap(byte[] topologyGeometryBin) {
    TopologyGeometryPartition partition;

    try {
      partition = TopologyGeometryPartition.parseFrom(topologyGeometryBin);
    } catch (InvalidProtocolBufferException ex) {
      throw new RuntimeException(ex);
    }
    return partition
        .getNodeList()
        .stream()
        .collect(
            Collectors.toMap(
                Node::getIdentifier,
                Node::getSegmentRefCount,
                (v1, v2) -> {
                  throw new RuntimeException(
                      String.format("Duplicate key for values %1$s and %2$s", v1, v2));
                },
                TreeMap::new));
  }
}
