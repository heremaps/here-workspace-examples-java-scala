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

package com.here.platform.data.validation.example.quickstart.testing.scala

import com.here.platform.data.processing.blobstore.Retriever
import com.here.platform.data.processing.catalog.Partition.ValueWrapper
import com.here.platform.data.processing.compiler.{InKey, InMeta}
import com.here.platform.pipeline.logging.LogContext
import com.here.platform.data.validation.core.testing.context.MapGroupFeatureLoader
import com.here.platform.data.validation.example.quickstart.testing.scala.LayerDefs.QuickstartTestingInputLayers
import com.here.platform.schema.data.validation.example.quickstart.input.v1.schema.LineSegments

/** This example implementation that extracts features for [[TestCase]]
  * simply extracts the LineSegments from the input partition's payload.
  *
  * @param retriever Retriever from the [[com.here.platform.data.processing.driver.DriverContext]] from
  *                  which to extract input payload data
  */
class FeatureLoader(retriever: Retriever)
    extends MapGroupFeatureLoader[LineSegments]
    with QuickstartTestingInputLayers {
  def loadFeatures(in: (InKey, InMeta))(implicit logContext: LogContext): LineSegments = {
    val segs = LineSegments.parseFrom(retriever.getPayload(in.key, in.value).content)
    segs
  }
}
