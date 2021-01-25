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

import com.here.platform.data.processing.compiler.OutKey
import com.here.platform.pipeline.logging.ContextLogging
import com.here.platform.data.validation.core.assessment.criteria.QualityCriteria
import com.here.platform.schema.data.validation.core.assessment.v1.assessment.TestAssessment
import com.here.platform.schema.data.validation.core.assessment.v1.assessment.TestAssessment.TestAssessmentResultType
import com.here.platform.schema.data.validation.example.quickstart.assessment.v1.assessment.Assessment
import com.here.platform.schema.data.validation.core.metrics.v1.metrics.TestMetricSeverity.SeverityType

// MapReduce implementation for assessment criteria
class Criteria(assessmentConfig: AssessmentConfig)
    extends QualityCriteria[Data, Assessment]
    with ContextLogging
    with LayerDefs.AssessmentOutLayer {
  def id: String = "quickstartcriteria"

  /** Assesses test metrics and tile counts into final assessment
    *
    * @param data       All test metrics and tile counts
    * @return Output assessment protobuf payload
    */
  def reduce(data: (OutKey, Iterable[Data])): Option[Assessment] = {
    val criticalCount = data.value
      .flatMap(ctx => ctx.metric)
      .flatMap(metric => metric.metrics)
      .filter(metricSample => metricSample.severity.value == SeverityType.CRITICAL)
      .map(metricSample => metricSample.tileIds.size)
      .sum

    val allCount = data.value
      .map(ctx => ctx.tileCount)
      .sum

    val criticalPercentage = criticalCount.toDouble / allCount

    val resultType = if (criticalPercentage > assessmentConfig.criticalThreshold) {
      TestAssessmentResultType.FAIL
    } else {
      TestAssessmentResultType.PASS
    }

    logger.info(s"assessment result is $resultType")

    val status = TestAssessment(id, resultType)

    Some(
      Assessment(
        status,
        criticalThreshold = Some(assessmentConfig.criticalThreshold),
        criticalPercent = Some(criticalPercentage),
        numCriticalErrors = Some(criticalCount.toLong),
        numCandidateTiles = Some(allCount.toLong)
      )
    )
  }
}
