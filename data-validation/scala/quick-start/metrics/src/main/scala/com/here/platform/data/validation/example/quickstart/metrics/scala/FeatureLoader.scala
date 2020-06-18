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

package com.here.platform.data.validation.example.quickstart.metrics.scala

import com.here.platform.data.processing.blobstore.Retriever
import com.here.platform.data.processing.catalog.Partition.{HereTile, ValueWrapper}
import com.here.platform.data.processing.catalog.{Catalog, Layer}
import com.here.platform.data.processing.compiler.{InKey, InMeta}
import com.here.platform.pipeline.logging.LogContext
import com.here.platform.data.validation.core.metrics.MetricsInputCatalog
import com.here.platform.data.validation.core.metrics.context.MapGroupFeatureLoader
import com.here.platform.schema.data.validation.example.quickstart.testing.v1.testing.Result

// Extracts test result and tile ID from input
class FeatureLoader(retriever: Retriever,
                    override val inputCatalogId: Catalog.Id,
                    override val testingLayer: Layer.Id)
    extends MapGroupFeatureLoader[Data]
    with MetricsInputCatalog {
  def loadFeatures(in: (InKey, InMeta))(implicit logContext: LogContext): Data = {
    val result = Result.parseFrom(retriever.getPayload(in.key, in.value).content)
    val tileId = in.key.partition.asInstanceOf[HereTile].tileId
    Data(tileId, result)
  }
}
