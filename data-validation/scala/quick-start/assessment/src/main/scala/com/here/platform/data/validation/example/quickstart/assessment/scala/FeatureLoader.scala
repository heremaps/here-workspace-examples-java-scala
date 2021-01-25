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

package com.here.platform.data.validation.example.quickstart.assessment.scala

import com.here.platform.data.processing.blobstore.Retriever
import com.here.platform.data.processing.compiler.{InKey, InMeta}
import com.here.platform.pipeline.logging.LogContext
import com.here.platform.data.validation.core.assessment.context.DirectFeatureLoader
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.metrics.Metric
import com.here.platform.data.validation.example.quickstart.common.{LayerDefs => CommonLayerDefs}

/** Loads assessment contexts from the input catalog.
  *
  * @param retriever Extracts payloads from the input metrics catalog.
  */
class FeatureLoader(retriever: Retriever)
    extends DirectFeatureLoader[Data]
    with LayerDefs.InputLayers {
  /** Extracts feature data from input layers.
    *
    * @param in key, meta pair from Subject layer
    * @param logContext Meta information added to subsequent logging output
    * @return Feature context data for the given input partition
    */
  def loadFeatures(in: (InKey, InMeta))(implicit logContext: LogContext): Data = {
    val catalogID = in.key.catalog
    catalogID match {
      case CommonLayerDefs.Assessment.In.metricsResultCatalogId =>
        Data(metric = Some(Metric.parseFrom(retriever.getPayload(in.key, in.meta).content)))
      case CommonLayerDefs.Testing.In.lineSegmentsCatalogId =>
        Data(tileCount = 1)
      case _ => throw new IllegalStateException("Unexpected input catalog: " + catalogID)
    }
  }
}
