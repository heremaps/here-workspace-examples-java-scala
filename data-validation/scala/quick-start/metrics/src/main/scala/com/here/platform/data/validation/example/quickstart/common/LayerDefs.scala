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

package com.here.platform.data.validation.example.quickstart.common

import com.here.platform.data.processing.catalog.Partition.Generic
import com.here.platform.data.processing.catalog.{Catalog, Layer}

object LayerDefs {
  val applicationName = "quickstart"

  object Testing {
    object In {
      val lineSegmentsCatalogId = Catalog.Id(s"${applicationName}input")
      val lineSegmentsLayer = Layer.Id("linesegments")
    }

    object Out {
      val testResultCatalogId = Catalog.Id(s"${applicationName}testing")
      val testResultLayerName = Layer.Id("test-result")
    }
  }

  object Metrics {
    object In {
      val testResultCatalogId = Testing.Out.testResultCatalogId
      val testResultLayerName = Testing.Out.testResultLayerName
    }

    object Out {
      val metricsCatalogId = Catalog.Id(s"${applicationName}metrics")
      val metricsResultLayerName = Layer.Id("metrics-result")
    }
  }

  object Assessment {
    object In {
      val metricsResultCatalogId = Metrics.Out.metricsCatalogId
      val metricsResultLayerName = Metrics.Out.metricsResultLayerName
      val candidateCatalogId = Testing.In.lineSegmentsCatalogId
      val candidateLayerName = Testing.In.lineSegmentsLayer
    }

    object Out {
      val assessmentCatalogId = Catalog.Id(s"${applicationName}assessment")
      val finalAssessmentLayer = Layer.Id("assessment")
      val finalAssessmentResultPartition = Generic("ASSESSMENT")
    }
  }
}
