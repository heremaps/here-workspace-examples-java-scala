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

import com.here.platform.data.processing.catalog.{Catalog, Layer}
import com.here.platform.data.validation.example.quickstart.common.{LayerDefs => CommonLayerDefs}

object LayerDefs {
  trait AssessmentOutLayer
      extends com.here.platform.data.validation.core.assessment.criteria.AssessmentOutLayer {
    val assessmentResultLayer = CommonLayerDefs.Assessment.Out.finalAssessmentLayer

    val assessmentResultPartition = CommonLayerDefs.Assessment.Out.finalAssessmentResultPartition
  }

  trait InputLayers extends com.here.platform.data.processing.compiler.InputLayers {
    def inLayers: Map[Catalog.Id, Set[Layer.Id]] = Map(
      CommonLayerDefs.Assessment.In.metricsResultCatalogId -> Set(
        CommonLayerDefs.Assessment.In.metricsResultLayerName),
      CommonLayerDefs.Assessment.In.candidateCatalogId -> Set(
        CommonLayerDefs.Assessment.In.candidateLayerName)
    )
  }
}
