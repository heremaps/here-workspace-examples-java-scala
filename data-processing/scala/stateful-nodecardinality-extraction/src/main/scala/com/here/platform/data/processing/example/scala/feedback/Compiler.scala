/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package com.here.platform.data.processing.example.scala.feedback

import com.here.platform.data.processing.blobstore.Payload
import com.here.platform.data.processing.catalog.Partition.{Key, Meta}
import com.here.platform.data.processing.compiler.{
  CompileOut1To1Fn,
  DirectMToNCompiler,
  InKey,
  OutKey
}
import com.here.platform.data.processing.driver.{Default, DriverContext}
import com.here.platform.pipeline.logging.ContextLogging
import com.here.platform.data.processing.spark.partitioner.{NameHashPartitioner, Partitioner}
import com.here.schema.rib.v2.topology_geometry_partition.TopologyGeometryPartition

import collection.breakOut
import scala.collection.immutable.SortedMap

/**
  * Shows how to use the output of the previous compilation as feedback input to count the number of times
  * the node cardinalities have changed for each partition.
  *
  * In this example output keys depend only on input keys, so the compiler most suited for this is one of the
  * <a href="https://developer.here.com/olp/documentation/data-processing-library/dev_guide/topics/functional-patterns.html#direct-1n-and-mn-compilers">Direct compilers</a>.
  * The Direct1ToNCompiler does not fit in this case, because in compileOutFn, we may have two
  * intermediate data instances for partition - one for input data and one for the feedback data
  * (if available). Consequently, we use the  [[DirectMToNCompiler]]
  *
  * Each output partition should be written to one output layer, once, so we use [[CompileOut1To1Fn]]
  *
  * [[DirectMToNCompiler]] extends [[com.here.platform.data.processing.compiler.OutputLayers]] and
  * [[com.here.platform.data.processing.compiler.InputLayers]] so we mix-in [[LayerCatalogDefs]] which
  * implement their methods
  *
  * @param ctx A gate for user to the features provided by the library.
  * Here it is used to get a { @link Retriever} for the single input catalog and one for the feedback catalog
  * that are created using the input catalog configuration provided.
  */
class Compiler(ctx: DriverContext)
    extends DirectMToNCompiler[(Key, Meta)]
    with CompileOut1To1Fn[(Key, Meta)]
    with ContextLogging
    with LayerCatalogDefs {

  // retriever from the input catalog
  private val retriever = ctx.inRetriever(CatalogDefs.InputCatalog)

  // retriever from the output catalog latest version (previous compilation)
  private val feedBackRetriever = ctx.feedbackRetriever

  /**
    * The input partitioner. Input partitions and feedback partitions (if available), have the same partition
    * name but different catalog ID and layer ID.
    * <a href="https://developer.here.com/olp/documentation/data-processing-library/dev_guide/topics/partitioners-spark-additions.html">Overview of available partitioners</a>.
    * A partitioner that uses only the partition name is the [[NameHashPartitioner]].
    *
    * @param parallelism The total number of partitions.
    * @return The input partitioner.
    */
  override def inPartitioner(parallelism: Int) = Some(NameHashPartitioner(parallelism))

  /**
    * The output partitioner. Output partition names are equal to the input partition names
    * (see [[mappingFn(Key)]] so we use the same partitioner as in
    * [[inPartitioner(int)]]; this prevents data shuffling.
    *
    * @param parallelism The number of partitions.
    * @return The same partitioner as the [[inPartitioner(int)]].
    */
  override def outPartitioner(parallelism: Int): Option[Partitioner[OutKey]] =
    inPartitioner(parallelism)

  /**
    * The [[DirectMToNCompiler]]'s output keys depend only on input keys. For each input partition
    * we expect to have an output partition with the same partition
    * name (say, a number like "410374") but with output catalog ID and layer ID.
    *
    * Consider that the current function processes the input from *all* input layers, so that
    * inKey.layer() may return either: [[LayersDefs.topologyGeometry]] as well as [[LayersDefs.nodeCardinality]].
    *
    * @return An [[Iterable]] with output partition.
    */
  override def mappingFn(inKey: Key) =
    Iterable(Key(Default.OutCatalogId, LayersDefs.nodeCardinality, inKey.partition))

  /**
    * Pass each input tuple to [[compileOutFn(Key, Iterable)]] function.
    *
    * Refer to `here.platform.data-processing.executors.compilein` part in
    * <a href="https://developer.here.com/olp/documentation/data-processing-library/dev_guide/topics/configuration.html">configuration page</a>
    * to check configuration options available for this function
    *
    * @param in The input partition's [[Key]] and [[Meta]] to process.
    * @return The value of intermediate data for [[compileOutFn(Key, Iterable)]] function.
    */
  override def compileInFn(in: (Key, Meta)): (Key, Meta) = in

  /**
    * The output depends on the input catalog partitions and on the previous compilation results.
    * We generate a partition containing the node cardinalities, we compare it with the previously
    * generated one, if available. If the content has changed, we also increment the run counter.
    *
    * Refer to `here.platform.data-processing.executors.compileout` part in
    * <a href="https://developer.here.com/olp/documentation/data-processing-library/dev_guide/topics/configuration.html">configuration page</a>
    * to check configuration options available for this function
    *
    * @param outKey       the key of the output partition to generate
    * @param intermediate non-empty [[Iterable]] of intermediate data as provided by compileInFn
    *                     which can have at most two entries: one for input layer and one for feedback layer.
    * @return [[Option]] of [[com.here.platform.data.processing.blobstore.Payload]]:
    *  - same as feedback data if input data not changed
    *  - new with updated counter if changes present or feedback input is empty
    *  - [[Option#EMPTY]] if input and feedback data not present
    */
  override def compileOutFn(outKey: OutKey,
                            intermediate: Iterable[(Key, Meta)]): Option[Payload] = {

    val inLayerEntries = intermediate.filter {
      _.key.layer == LayersDefs.topologyGeometry
    }
    val feedbackLayerEntries = intermediate.filter {
      _.key.layer == LayersDefs.nodeCardinality
    }

    require(inLayerEntries.size <= 1)
    require(feedbackLayerEntries.size <= 1)

    val optionInLayerMeta = inLayerEntries.headOption
    val optionFeedbackLayerMeta = feedbackLayerEntries.headOption

    // obtain cardinality map from input partition
    val optCardinalityMap = optionInLayerMeta
      .map(tgData =>
        createNodeCardinalityMap(retriever.getPayload(tgData.key, tgData.meta).content))

    val optFeedbackBin = optionFeedbackLayerMeta
      .map(feedback => feedBackRetriever.getPayload(feedback.key, feedback.meta).content)

    val optFeedback = optFeedbackBin.map(NodeCardinalityRunCounter.fromByteArray)
    val isHashMatch = optFeedback.map(_.hash) == optCardinalityMap.map(_.hashCode())

    val binaryOutput = (isHashMatch, optCardinalityMap, optFeedback) match {
      // no changes, or geometry-topology is absent
      case (true, _, _) | (_, None, _) => optFeedbackBin
      // empty feedback layer, first run
      case (_, Some(card), None) =>
        Some(NodeCardinalityRunCounter(card, 1, card.hashCode).toByteArray)
      // input partition changed since last run
      case (_, Some(card), Some(feed)) =>
        Some(NodeCardinalityRunCounter(card, feed.updatesCount + 1, card.hashCode).toByteArray)
    }

    binaryOutput.map(Payload(_))
  }

  /**
    * Creates a [[Map[String, Int]] that contains all nodes and their cardinality
    * from a given serialized [[TopologyGeometryPartition]].
    * The cardinalities are stored in a sorted map to make sure the output data is deterministic
    *
    * @param topologyGeometryPartitionBin to process
    * @return prepared [[Map[String, Int]]
    */
  def createNodeCardinalityMap(
      topologyGeometryPartitionBin: Array[Byte]): SortedMap[String, Int] = {
    val topologyGeometryPartition =
      TopologyGeometryPartition.parseFrom(topologyGeometryPartitionBin)
    topologyGeometryPartition.node
      .map(n => (n.identifier, n.segmentRef.size))(breakOut)
  }
}
