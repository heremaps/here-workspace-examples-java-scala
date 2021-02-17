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

package com.here.platform.data.processing.example.java.difftool.processor;

import com.google.common.collect.Sets;
import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Payload;
import com.here.platform.data.processing.java.blobstore.Retriever;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.compiler.CompileOut1To1Fn;
import com.here.platform.data.processing.java.compiler.DirectMToNCompiler;
import com.here.platform.data.processing.java.driver.Default;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.spark.partitioner.PartitionerOfKey;
import com.here.schema.rib.v2.TopologyGeometry.Segment;
import com.here.schema.rib.v2.TopologyGeometryPartitionOuterClass.TopologyGeometryPartition;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Shows how to compute the difference between two versions of an input catalog with output to JSON
 *
 * <p>In this example output keys depend only on input keys, so the compiler most suited for this is
 * one of the <a
 * href="https://developer.here.com/documentation/data-processing-library/dev_guide/topics/functional-patterns.html#direct-1n-and-mn-compilers">Direct
 * compilers</a>. The Direct1ToNCompiler does not fit in this case, because in compileOutFn, we may
 * have two intermediate data instances for partition - one for input data and one for the previous
 * version data (if available). Consequently, we use the {@link DirectMToNCompiler}
 *
 * <p>Each output partition should be written to one output layer, once, so we use {@link
 * CompileOut1To1Fn}
 */
public class Compiler
    implements DirectMToNCompiler<IntermediateData>, CompileOut1To1Fn<IntermediateData> {

  /** retriever to get input data */
  private Retriever currentRetriever;

  /** retriever to get previous data */
  private Retriever previousRetriever;

  /**
   * Constructor
   *
   * @param ctx A gate for user to the facilities provided by the framework. Here it is used to get
   *     access to data retrievers that are created using the provided input catalog configuration
   *     and also to get Spark parallelism needed for partitioner
   */
  Compiler(DriverContext ctx) {
    currentRetriever = ctx.inRetriever(Defs.In.RIB_CATALOG);
    previousRetriever = ctx.inRetriever(Defs.In.PREVIOUS_RIB);
  }

  /**
   * The partitioner for the input data
   *
   * <p>As this option is None, the Executor uses a default Hash Partitioner. For this example
   * default should be enough as we do not have references to neighbour partitions
   *
   * @param parallelism The parallelism of the partitioner, this parameter is ignored
   * @return empty, which mean using default Hash Partitioner
   */
  @Override
  public Optional<PartitionerOfKey> inPartitioner(int parallelism) {
    return Optional.empty();
  }

  /**
   * The output partitioner
   *
   * <p>As this option is None, the Executor uses a default Hash Partitioner. For this example
   * default should be enough as we do not have references to neighbour partitions
   *
   * @param parallelism the number of partitioner, this parameter is ignored
   * @return empty, which mean using default Hash Partitioner
   */
  @Override
  public Optional<PartitionerOfKey> outPartitioner(int parallelism) {
    return Optional.empty();
  }

  /**
   * Calculates which output partitions the given single input partition impacts. In this example
   * mapping is 1-to-1, partition of outKey is same as of inKey, with configured Output Layer Id and
   * default output catalog ID
   *
   * @return the output partitions that the given input partition maps to
   */
  @Override
  public Iterable<Key> mappingFn(Key inKey) {
    Key outKey = new Key(Default.OutCatalogId(), Defs.Out.LAYER_NAME, inKey.partition());
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
    return Defs.LayerDefs.outLayers;
  }

  /**
   * Calculates the intermediate result from a single input partition. The result will be provided
   * together with the input key in the {@link
   * com.here.platform.data.processing.java.compiler.CompileOut1To1Fn}.
   *
   * @param in the input partition to process
   * @return the value of intermediate data of type Data for this partition. This value will be
   *     passed in {@link}com.here.platform.data.processing.compiler.CompileOutFn]] to all output
   *     keys impacted by the in partition.
   */
  @Override
  public IntermediateData compileInFn(Pair<Key, Meta> in) {
    return new IntermediateData(in.getKey(), in.getValue());
  }

  /**
   * Compiles a single output partition.
   *
   * @param outKey the key of the output partition to generate
   * @param intermediate non-empty {@link Iterable} of intermediate data as provided by compileInFn
   *     which is required to compile the output partition. This collection will have multiple
   *     entries of the same value in case the same value was returned for this outKey by multiple
   *     compileInFn executions (for different input keys). outKeys which do not have any
   *     intermediate values assigned from the compileInFn calls get deleted automatically.
   * @return {@link Optional} of {@link com.here.platform.data.processing.blobstore.Payload}, this
   *     method is to return exactly one element, with the same values for the output key as passed
   *     in outKey. either the partition content, or None if there is no data to be published.
   */
  @Override
  public Optional<Payload> compileOutFn(Key outKey, Iterable<IntermediateData> intermediate) {

    Map<String, Segment> newSegments =
        getByCatalogId(intermediate, Defs.In.RIB_CATALOG, currentRetriever);
    Map<String, Segment> oldSegments;

    oldSegments = getByCatalogId(intermediate, Defs.In.PREVIOUS_RIB, previousRetriever);

    Set<String> addedSegments = Sets.difference(newSegments.keySet(), oldSegments.keySet());
    Set<String> removedSegments = Sets.difference(oldSegments.keySet(), newSegments.keySet());
    Set<String> modifiedSegments =
        Sets.intersection(oldSegments.keySet(), newSegments.keySet())
            .stream()
            .filter(
                seg ->
                    !newSegments.get(seg).getGeometry().equals(oldSegments.get(seg).getGeometry()))
            .collect(Collectors.toSet());

    if (!modifiedSegments.isEmpty() || !removedSegments.isEmpty() || !addedSegments.isEmpty()) {
      GeometryDiff diff =
          new GeometryDiff(
              toSortedList(modifiedSegments),
              toSortedList(removedSegments),
              toSortedList(addedSegments));
      return Optional.of(new Payload(diff.toByteArray()));
    } else {
      return Optional.empty();
    }
  }

  private static List<String> toSortedList(Set<String> s) {
    List<String> list = new ArrayList<String>(s);
    Collections.sort(list);
    return list;
  }

  private Map<String, Segment> getSegments(Retriever ret, IntermediateData keyMeta) {
    try {
      TopologyGeometryPartition partition =
          TopologyGeometryPartition.parseFrom(ret.getPayload(keyMeta.key, keyMeta.meta).content());
      return partition
          .getSegmentList()
          .stream()
          .collect(Collectors.toMap(Segment::getIdentifier, seg -> seg));
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Segment> getByCatalogId(
      Iterable<IntermediateData> intermediateData, String catalogId, Retriever retriever) {
    return StreamSupport.stream(intermediateData.spliterator(), false)
        .filter(data -> data.key.catalog().equals(catalogId))
        .findFirst()
        .map(data -> getSegments(retriever, data))
        .orElse(Collections.emptyMap());
  }
}
