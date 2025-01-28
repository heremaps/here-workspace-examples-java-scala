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

package com.here.platform.data.processing.example.scala.validation

import com.here.platform.data.processing.blobstore.caching.CachingMappingRetriever
import com.here.platform.data.processing.catalog.{Catalog, Layer, Partition}
import com.here.platform.data.processing.driver.DeltaSimpleSetup
import com.here.platform.data.processing.driver.config.CompleteConfig
import com.here.platform.data.processing.driver.deltasets.ResolutionStrategy.{
  BackwardResolution,
  DirectQuery
}
import com.here.platform.data.processing.driver.deltasets.{
  DeltaContext,
  OneToOne,
  PreservesPartitioning,
  PublishedSet
}
import com.here.platform.data.processing.driver.runner.pipeline.PipelineRunner
import com.here.schema.rib.v2.topology_geometry_partition.TopologyGeometryPartition
import com.here.platform.data.processing.catalog.Implicits._
import com.here.platform.data.processing.spark.partitioner.LocalityAwarePartitioner
import com.here.platform.data.processing.validation.scalatest.ScalatestSuite
import com.here.platform.data.processing.validation.{JsonSerializer, SuiteCompiler}
import com.here.platform.pipeline.logging.ContextLogging
import org.json4s.DefaultFormats

/** Custom assessment class.
  *
  * @param isSucceeded Whether the validation job has succeeded (data is valid) or not (data is
  *                    invalid).
  * @param failureRate Additional information about the failure rate.
  */
case class Assessment(isSucceeded: Boolean, failureRate: Double)

object Main extends PipelineRunner with DeltaSimpleSetup with ContextLogging {
  // Change this every time you change the compilation logic, to notify the Data Processing Library
  // that the business logic has changed, and incremental compilation must be disabled for one run.
  override def applicationVersion: String = "1.0.0"

  // Input catalog ID
  val hmc = Catalog.Id("hmc")

  // Input layer ID
  val topologyGeometry = Layer.Id("topology-geometry")

  override def setupSets(
      completeConfig: CompleteConfig,
      context: DeltaContext
  ): Iterable[PublishedSet] = {
    import context.transformations._

    val retriever =
      new CachingMappingRetriever[TopologyGeometryPartition](context.inRetriever(hmc), {
        (_, payload) =>
          TopologyGeometryPartition.parseFrom(payload.content)
      }, statistics = Some(context.driverContext.statistics))

    // Test data extraction: load test data into a
    // `DeltaSet[Partition.HereTile, TopologyGeometryData]`

    // We use a `LocalityAwarePartitioner` because we have references between topology partitions
    // and we cache the decoded partitions
    val partitioner = LocalityAwarePartitioner(context.defaultParallelism, 11)

    val topologyMetadata = context.queryCatalogLayer(hmc, topologyGeometry, partitioner)

    val testData = topologyMetadata
      .mapValuesWithResolver(
        {
          case (resolver, key, meta) =>
            val partition = retriever.getPayloadAndMap(key, meta)

            // Collect all tiles referenced from the nodes and the segments of the current partition
            val fromNode = partition.node.iterator.flatMap(_.segmentRef)
            val fromSegment = partition.segment.iterator
              .flatMap(s => Iterable(s.startNodeRef.get, s.endNodeRef.get))

            // Transform all references in the set of referenced keys
            val referencedPartitions: Set[Partition.Key] = (fromNode ++ fromSegment)
              .map(
                reference =>
                  Partition
                    .Key(hmc, topologyGeometry, Partition.HereTile(reference.partitionName.toLong))
              )
              .toSet

            // Create an instance of the data under test
            TopologyGeometryData(
              key.partition.hereTileId,
              partition,
              referencedPartitions.iterator.flatMap { key =>
                resolver
                  .resolve(key)
                  .map(meta => key.partition.toString -> retriever.getPayloadAndMap(key, meta))
              }.toMap
            )
        },
        List(
          BackwardResolution.toNeighbors(topologyMetadata, hmc, topologyGeometry, 3),
          DirectQuery(hmc, Set(topologyGeometry))
        )
      )
      .withId("mapValuesWithResolver")
      // transform the Partition.Key's to Partition.HereTile's. We can use `PreservesPartitioning`
      // because the partitioner we use is a `PartitionNamePartitioner` which maps Partition.Key's
      // and their Partition.Name's to the same Spark partition
      .mapKeys(OneToOne.toHereTile(hmc, topologyGeometry), PreservesPartitioning)

    // Import default JSON serializers. Used to serialize/deserialize the `Report` and `Metrics`
    // produced by our suite
    import com.here.platform.data.processing.validation.DefaultJsonSerializers._

    // Import `publishAndAggregateByLevel` and `assess` transformations
    import com.here.platform.data.processing.validation.Transformations._

    // To enable the transformations in
    // `com.here.platform.data.processing.validation.Transformations` we need an implicit
    // `DeltaContext`
    implicit val ctx: DeltaContext = context

    // Create a `SuiteCompiler` to run and compile our suite
    val suiteCompiler = new SuiteCompiler(new ScalatestSuite(classOf[TopologyGeometrySuite]))

    val (resultsAndMetricsPublishedSet, aggregatedMetrics) = testData
    // [inject] Uncomment the following line to inject some "dangling reference" errors
    // .mapValues(ErrorInjection.injectError)
      .mapValues(suiteCompiler.compile)
      .publishAndAggregateByLevel(suiteCompiler.outLayers, suiteCompiler.metricsLayer)

    // Assess results
    implicit val assessmentSerializer: JsonSerializer[Assessment] =
      new JsonSerializer[Assessment](DefaultFormats)

    val assessmentPublishedSet = aggregatedMetrics
      .assess[Assessment]() { metrics =>
        // In this example we simply assess that no tests have failed. A more sophisticated
        // assessment logic would use the accumulated values and the statistics about each specific
        // suite or test-case to reach a final verdict. We also store the final failure rate
        val assessment =
          Assessment(metrics.stats.failed == 0, metrics.stats.failed.toDouble / metrics.stats.total)
        logger.info(s"Final assessment: $assessment")
        assessment
      }

    Iterable(resultsAndMetricsPublishedSet, assessmentPublishedSet)
  }
}

object ErrorInjection {
  // Simple error injection function. Every 4 tiles it drops some segments and nodes from the data
  // under test, therefore creating dangling references that are detected by the test suite
  def injectError(data: TopologyGeometryData): TopologyGeometryData =
    if (data.tileId % 4 != 0) {
      data
    } else {
      data.copy(
        partition = data.partition
          .copy(segment = data.partition.segment.drop(3), node = data.partition.node.drop(3))
      )
    }
}
