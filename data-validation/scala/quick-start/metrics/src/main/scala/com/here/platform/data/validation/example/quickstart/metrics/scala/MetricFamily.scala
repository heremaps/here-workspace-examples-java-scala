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

package com.here.platform.data.validation.example.quickstart.metrics.scala

import com.here.platform.data.processing.catalog.Layer
import com.here.platform.data.validation.core.metrics.calculators.families.SingleCalculator
import com.here.platform.data.validation.example.quickstart.common.{LayerDefs => CommonLayerDefs}
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.metrics.Metric

/** Example MetricFamily implementation that has a single metric.
  *
  */
class MetricFamily(override val mapReduce: Calculator)
    extends SingleCalculator[Data, Metric](mapReduce) {
  val id: String = "quickstartmetricfamily"

  def toByteArray(r: Metric): Array[Byte] = r.toByteArray

  override def outLayers: Set[Layer.Id] = Set(CommonLayerDefs.Metrics.Out.metricsResultLayerName)
}
