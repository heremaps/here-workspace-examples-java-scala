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

package com.here.platform.data.validation.example.quickstart.comparison.scala

import com.here.platform.data.processing.blobstore.{Payload, Retriever}
import com.here.platform.data.processing.catalog.{Layer, Partition}
import com.here.platform.data.processing.compiler.{InKey, InMeta, OutKey}
import com.here.platform.data.processing.driver.Default
import com.here.platform.pipeline.logging.RootLogContext
import com.here.platform.data.validation.core.comparison.ContextHelper
import com.here.platform.data.validation.core.comparison.metadiff.grouped.{
  DiffByLayers,
  DiffByPartitions
}
import com.here.platform.schema.data.validation.example.quickstart.comparison.v1.schema.ComparedLineSegments
import com.here.platform.schema.data.validation.example.quickstart.input.v1.schema.LineSegments

class QuickStartComparison(referenceRetriever: Retriever, candidateRetriever: Retriever)
    extends DiffByPartitions()
    with Serializable {
  implicit val logContext: RootLogContext.type = RootLogContext

  override def handleDiff(partition: Partition.Name,
                          pairsPerLayer: Iterable[(Layer.Id, Option[InMeta], Option[InMeta])])
      : Iterable[(OutKey, Option[Payload])] =
    pairsPerLayer.map(l => {
      val layerName = l._1

      if (layerName == LayerNames.hereTiledInputLayer) {
        val result: ComparedLineSegments = combineLineSegments(partition, layerName, l._2, l._3)
        (OutKey(Default.OutCatalogId, LayerNames.hereTiledOutputLayer, partition),
         Some(Payload(result.toByteArray)))
      } else {
        val genericName = (layerName.name + "-" + partition)
        (OutKey(Default.OutCatalogId, LayerNames.genericOutLayer, Partition.Generic(genericName)),
         Some(Payload("\"checksum differs\"".getBytes())))
      }
    })

  private def combineLineSegments(partition: Partition.Name,
                                  layerName: Layer.Id,
                                  referenceMetaOpt: Option[InMeta],
                                  candidateMetaOpt: Option[InMeta]) = {
    def extractMetaOpt(referenceKey: InKey, retriever: Retriever, value: Option[InMeta]) =
      value match {
        case Some(meta: InMeta) =>
          Some(retriever.getPayload(referenceKey, meta))
        case None => None
      }

    val referencePayload =
      extractMetaOpt(InKey(ContextHelper.REFERENCECATID, layerName, partition),
                     referenceRetriever,
                     referenceMetaOpt)
    val candidatePayload =
      extractMetaOpt(InKey(ContextHelper.CANDIDATECATID, layerName, partition),
                     candidateRetriever,
                     candidateMetaOpt)

    val candidate = LineSegments.parseFrom(candidatePayload.get.content).geometry
    val reference = LineSegments.parseFrom(referencePayload.get.content).geometry

    ComparedLineSegments.defaultInstance
      .withReferenceGeometry(reference)
      .withCandidateGeometry(candidate)
  }
}

class QuickStartComparisonByLayers() extends DiffByLayers() with Serializable {
  override def handleDiff(layer: Layer.Id,
                          partitioned: Iterable[(Partition.Name, Option[InMeta], Option[InMeta])])
      : Iterable[(OutKey, Option[Payload])] = {
    val start = """"{"partitions":[{"""
    val end = """}]}"""
    Seq(
      (OutKey(Default.OutCatalogId, LayerNames.genericOutLayer, Partition.Generic(layer.name)),
       Some(Payload(partitioned.map(p => { p._1.toString }).mkString(start, ",", end).getBytes))))
  }
}
