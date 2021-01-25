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

package com.here.platform.data.processing.example.scala.difftool.processor

import java.nio.charset.StandardCharsets

import com.here.platform.data.processing.blobstore.{Payload, Retriever}
import com.here.platform.data.processing.compiler._
import com.here.platform.data.processing.driver.DriverContext
import com.here.platform.data.processing.spark.partitioner.Partitioner
import com.here.platform.data.processing.example.scala.difftool.processor.LayersDefinitions._
import com.here.schema.rib.v2.topology_geometry._
import com.here.schema.rib.v2.topology_geometry_partition._

import scala.collection.breakOut

object Compiler {
  implicit class RichBoolean(val b: Boolean) extends AnyVal {
    final def option[A](a: => A): Option[A] = if (b) Some(a) else None
  }
}

/**
  * Direct MtoN functional pattern batch processor
  * <p>
  * This batch processor functional pattern is suited for tasks such as processing map tiles.
  * For more information, see the Data Processing Library documentation
  * at https://developer.here.com/olp/documentation.
  */
class Compiler(context: DriverContext)
    extends DirectMToNCompiler[IntermediateData]
    with CompileOut1To1Fn[IntermediateData]
    with LayerDefinitions {
  import Compiler._

  private val previousRetriever: Retriever = context.inRetriever(In.PreviousRib)

  private val currentRetriever: Retriever = context.inRetriever(In.Rib)

  /** The partitioner for the input data
    *
    * If this option is None, the Executor uses a default partitioner.
    * Set this option to apply the given partitioner when querying the
    * input catalogs.
    *
    * @param parallelism The parallelism of the partitioner
    * @return The input optional partitioner with the given parallelism
    *
    */
  override def inPartitioner(parallelism: Int): Option[Partitioner[InKey]] =
    None

  /** The partitioner for the output data
    *
    * If this option is None, the Executor uses a default partitioner.
    * Set this option to apply the given partitioner
    * when querying the output catalogs and producing data to be published.
    *
    * @param parallelism The parallelism of the partitioner
    * @return The output optional partitioner with the given parallelism
    *
    */
  override def outPartitioner(parallelism: Int): Option[Partitioner[OutKey]] =
    None

  /** Calculates the output partition key using the given single input partition key. The catalog and layer
    * values are replaced with the output catalog and output layer respectively; partition ID remains the same.
    * Such mappingFn used because we want to map the current partition key and the corresponding
    * output key from the previous run onto the same out key (in order to perform the diff).
    * Additionally, this data is processed by [[compileOutFn(Key, Iterable)]].
    *
    * @param inKey The input partition being mapped
    * @return The output partitions to which the given input partition maps
    *
    */
  override def mappingFn(inKey: InKey): Iterable[OutKey] =
    Iterable(inKey.copy(catalog = outCatalogId, layer = Out.LayerName))

  /** Calculates the intermediate result from a single input partition. This result is
    * provided together with the input key in [[com.here.platform.data.processing.compiler.direct.CompileOutFn]].
    *
    * @param in The input partition to process
    * @return The value of intermediate data of type `T` for this partition. This value is passed
    *         in [[com.here.platform.data.processing.compiler.direct.CompileOutFn]] to all output keys impacted by the in partition.
    *
    */
  override def compileInFn(in: (InKey, InMeta)): IntermediateData =
    IntermediateData(in.key, in.meta)

  /** Compiles a single output partition.
    *
    * @param outKey The key of the output partition to generate
    * @param intermediate Non-empty [[scala.Iterable]] of intermediate data as provided by compileInFn that is
    *                     required to compile the output partition. This collection has multiple entries with the
    *                     same value if the same value was returned for this outKey by multiple compileInFn
    *                     executions (for different input keys).
    *                     outKeys that do not have any intermediate values assigned from the compileInFn calls get
    *                     deleted by the framework automatically.
    * @return [[scala.Option]] of [[com.here.platform.data.processing.blobstore.Payload]], this
    *         method returns exactly one element with the same values for the output key as passed in outKey.
    *         either the partition content or None if there is no data to be published.
    *
    */
  override def compileOutFn(outKey: OutKey,
                            intermediate: Iterable[IntermediateData]): Option[Payload] = {
    def getSegments(retriever: Retriever)(keyMeta: IntermediateData): Map[String, Segment] = {
      val partition =
        TopologyGeometryPartition.parseFrom(retriever.getPayload(keyMeta.key, keyMeta.meta).content)
      partition.segment.map(x => (x.identifier, x))(breakOut)
    }

    val oldSegments: Map[String, Segment] =
      intermediate
        .find(_.key.catalog == LayersDefinitions.In.PreviousRib)
        .map(getSegments(previousRetriever))
        .getOrElse(Map.empty)

    val newSegments =
      intermediate
        .find(_.key.catalog == LayersDefinitions.In.Rib)
        .map(getSegments(currentRetriever))
        .getOrElse(Map.empty)

    val addedSegments = newSegments.keySet -- oldSegments.keySet
    val removedSegments = oldSegments.keySet -- newSegments.keySet
    val modifiedSegments =
      (oldSegments.keySet intersect newSegments.keySet).filter { segmentId =>
        oldSegments(segmentId).geometry != newSegments(segmentId).geometry
      }

    (addedSegments.nonEmpty || removedSegments.nonEmpty || modifiedSegments.nonEmpty)
      .option {
        def toJSONList(identifiers: Set[String]): String =
          s"[${identifiers.toList.sorted
            .map { x =>
              s""""$x""""
            }
            .mkString(", ")}]"

        val json =
          s"""
             |{
             |  "addedSegments": ${toJSONList(addedSegments)},
             |  "removedSegments": ${toJSONList(removedSegments)},
             |  "modifiedSegments": ${toJSONList(modifiedSegments)}
             |}
          """.stripMargin

        Payload(json.getBytes(StandardCharsets.UTF_8))
      }
  }
}
