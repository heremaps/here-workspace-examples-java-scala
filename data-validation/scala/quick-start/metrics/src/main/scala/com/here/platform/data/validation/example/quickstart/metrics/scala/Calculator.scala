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

import com.here.platform.data.processing.driver.Default
import com.here.platform.data.processing.catalog.Partition.Generic
import com.here.platform.data.processing.compiler.{InKey, OutKey}
import com.here.platform.data.validation.example.quickstart.common.LayerDefs
import com.here.platform.schema.data.validation.core.metrics.v1.metrics.TestMetricSeverity
import com.here.platform.schema.data.validation.core.testing.v1.testing.TestResult.TestResultType
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.metrics.{
  Metric,
  MetricSample
}

// The actual map-reduce implementation that assigns severities to the test results
class Calculator(val metricsConfig: MetricsConfig)
    extends com.here.platform.data.validation.core.metrics.calculators.Metric[Data, Metric] {
  val id: String = "quickstartmetriccalc"

  /** Maps input data into output partitions, restructuring into new feature contexts as needed.
    *
    * @param context    Feature context for the given input partition
    * @return Feature contexts for each output partition mapped from the input data
    */
  override def map(context: (InKey, Data)): Iterable[(OutKey, Data)] = {
    val outKey = OutKey(catalog = Default.OutCatalogId,
                        layer = LayerDefs.Metrics.Out.metricsResultLayerName,
                        partition = Generic(context.value.result.result.value.toString))
    Iterable((outKey, context.value))
  }

  /** Receives feature contexts from mapper and packages them into output protobufs.
    *
    * @param data       All the feature contexts for the given output partition, as produced by the mapper
    * @return The reduction output as optional
    */
  override def reduce(data: (OutKey, Iterable[Data])): Option[Metric] = {
    val tileIds = data.value.map(_.tileID).toSeq
    val isPass = data.key.partition.asInstanceOf[Generic].name.equals(TestResultType.PASS.name)
    if (tileIds.nonEmpty) {
      if (isPass && !metricsConfig.writeSeverityNoneSamples) {
        // If this is a PASS partition and we are not configured to write SeverityNone samples, we can skip writing output.
        None
      } else {
        // PASS results are considered SeverityNone; all other statuses are CRITICAL
        val metricSample: MetricSample = MetricSample(
          severity = TestMetricSeverity(metricid = id,
                                        value =
                                          if (isPass) TestMetricSeverity.SeverityType.NONE
                                          else TestMetricSeverity.SeverityType.CRITICAL),
          tileIds = tileIds
        )
        Some(Metric(metrics = Seq(metricSample)))
      }
    } else {
      // If we encountered no tile IDs, we have nothing to output.
      None
    }
  }
}
