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

import com.here.platform.data.processing.catalog.{Catalog, Layer}
import com.here.platform.data.processing.compiler.InputLayers
import com.here.platform.data.validation.core.testing.testcases.TestingOutLayer
import com.here.platform.data.validation.example.quickstart.common.{LayerDefs => CommonLayerDefs}

object LayerDefs {
  trait QuickstartTestingInputLayers extends InputLayers {
    val inLayers: Map[Catalog.Id, Set[Layer.Id]] =
      Map(
        CommonLayerDefs.Testing.In.lineSegmentsCatalogId -> Set(
          CommonLayerDefs.Testing.In.lineSegmentsLayer))
  }

  trait QuickstartTestingOutputLayer extends TestingOutLayer {
    val testResultLayer: Layer.Id = CommonLayerDefs.Testing.Out.testResultLayerName
  }
}
