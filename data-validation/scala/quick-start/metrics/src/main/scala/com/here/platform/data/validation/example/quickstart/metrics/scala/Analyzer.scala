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

import com.here.platform.data.processing.driver.DriverContext
import com.here.platform.data.validation.core.metrics.analyzers.MapGroupAnalyzer
import com.here.platform.data.validation.example.quickstart.common.LayerDefs
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.metrics.Metric

// The map-group processor that assigns metrics from the test results.
class Analyzer(override val mapReduceFamily: MetricFamily,
               override val featureLoader: FeatureLoader)
    extends MapGroupAnalyzer[Data, Metric](mapReduceFamily, featureLoader)

object Analyzer {
  /** Builds the compiler.
    *
    * @param context the driver context
    *
    */
  def apply(context: DriverContext, metricsConfig: MetricsConfig): Analyzer = {
    val metricsFamily = new MetricFamily(new Calculator(metricsConfig))

    val retriever = context.inRetriever(LayerDefs.Metrics.In.testResultCatalogId)
    val featureLoader =
      new FeatureLoader(retriever,
                        LayerDefs.Metrics.In.testResultCatalogId,
                        LayerDefs.Metrics.In.testResultLayerName)

    new Analyzer(metricsFamily, featureLoader)
  }
}
