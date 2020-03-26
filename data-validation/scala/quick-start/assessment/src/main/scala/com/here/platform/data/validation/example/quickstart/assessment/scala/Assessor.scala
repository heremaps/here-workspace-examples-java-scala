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

package com.here.platform.data.validation.example.quickstart.assessment.scala

import com.here.platform.data.processing.driver.DriverContext
import com.here.platform.data.validation.core.assessment.assessors.DirectAssessor
import com.here.platform.data.validation.example.quickstart.common.{LayerDefs => CommonLayerDefs}
import com.here.platform.schema.data.validation.example.quickstart.assessment.v1.assessment.Assessment

/** Simply aggregates the metrics results and distills it into a true-or-false state.
  *
  */
class Assessor(override val mapReduceFamily: CriteriaFamily,
               override val featureLoader: FeatureLoader)
    extends DirectAssessor[Data, Assessment](mapReduceFamily, featureLoader)

object Assessor {

  /** Builds the compiler.
    *
    * @param context the driver context
    *
    */
  def apply(context: DriverContext, assessmentConfig: AssessmentConfig): Assessor = {

    val assessmentFamily = new CriteriaFamily(new Criteria(assessmentConfig))

    val retriever = context.inRetriever(CommonLayerDefs.Assessment.In.metricsResultCatalogId)
    val featureLoader = new FeatureLoader(retriever)

    new Assessor(assessmentFamily, featureLoader)
  }
}
