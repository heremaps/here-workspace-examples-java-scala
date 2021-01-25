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

package com.here.platform.data.processing.example.scala.pedestrian.deltasets

import com.here.platform.data.processing.driver._
import com.here.platform.data.processing.driver.config.CompleteConfig
import com.here.platform.data.processing.driver.deltasets.ResolutionStrategy.BackwardResolution
import com.here.platform.data.processing.driver.deltasets.{
  DeltaContext,
  PreservesPartitioning,
  PublishedSet
}
import com.here.platform.data.processing.driver.runner.pipeline.PipelineRunner
import com.here.platform.data.processing.spark.partitioner.LocalityAwarePartitioner
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._ // import value reader for case classes
import com.here.platform.data.processing.utils.FicusValueReaders._ // import value read for PartitionKeyFilterConfig

object Main extends PipelineRunner with DeltaSimpleSetup {
  override def applicationVersion: String = "1.0.0"

  def setupSets(completeConfig: CompleteConfig, context: DeltaContext): Iterable[PublishedSet] = {
    val ribRetriever = context.inRetriever(Defs.In.RibCatalog)
    val config = completeConfig.compilerConfig.as[CompilerConfig]

    // The following import enables transformations on DeltaSets.
    import context.transformations._

    // Using a locality aware partitioner at level 12 will guarantee that all tiles at higher zoom level (13, 14 etc.)
    // are processed on the same worker node as the corresponding level 12 HERE Map content tiles. This way, we can
    // avoid shuffling.
    val partitioner = LocalityAwarePartitioner(context.defaultParallelism, 12)

    // We use a "compiler" object to encapsulate all code and data that may be sent to worker nodes and must therefore
    // be serializable.
    val compiler = new Compiler(config, ribRetriever)

    // Query the topology-geometry layer to read the road geometries.
    val topologyGeometries =
      context
        .queryCatalogLayer(Defs.In.RibCatalog, Defs.In.TopologyGeometryLayer, partitioner)

    // Process all road attributes partitions, while resolving references to topology-geometry partitions.
    val pedestrianFeatures =
      context
        .queryCatalogLayer(Defs.In.RibCatalog, Defs.In.RoadAttributesLayer, partitioner)
        .mapValuesWithResolver(
          compiler.extractPedestrianFeatures,
          // We pair each topology partition with the road partition of the same tile.
          List(
            BackwardResolution.toSamePartition(topologyGeometries,
                                               Defs.In.RibCatalog,
                                               Defs.In.RoadAttributesLayer)
          )
        )

    val outputTiles =
      pedestrianFeatures.flatMapGroup(
        (_, features) =>
          features.map { f =>
            (compiler.getOutputKey(f), f)
          },
        // If we increase the zoom level of the tiles, our LocalityAwarePartitioner will guarantee that no data needs
        // to be shuffled.
        if (config.outputLevel > 12) PreservesPartitioning else partitioner
      )

    // Convert the result to JSON and publish it.
    val published =
      outputTiles
        .mapValuesWithKey(compiler.toJSON)
        .publish(Set(Defs.Out.PedestrianSegments))

    // Return the published data. The calling context will finally commit the data as a new version of the output
    // catalog.
    Iterable(published)
  }
}
