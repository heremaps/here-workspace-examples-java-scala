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

package com.here.platform.data.validation.example.quickstart.comparison.scala

import com.here.platform.data.processing.catalog.Catalog.Id
import com.here.platform.data.processing.catalog.Layer
import com.here.platform.data.processing.compiler.{InputLayers, OutputLayers}
import com.here.platform.data.validation.core.comparison.ContextHelper

object LayerNames {
  final val hereTiledOutputLayer = Layer.Id("heretile-comparison-results")
  final val genericOutLayer = Layer.Id("generic-comparison-results")
  final val hereTiledInputLayer = Layer.Id("linesegments")
  final val genericInputLayer = Layer.Id("state")
}

trait LayerDefs extends InputLayers with OutputLayers {
  override def inLayers: Map[Id, Set[Layer.Id]] =
    Map(
      ContextHelper.CANDIDATECATID -> Set(LayerNames.hereTiledInputLayer,
                                          LayerNames.genericInputLayer))
  override def outLayers: Set[Layer.Id] =
    Set(LayerNames.hereTiledOutputLayer, LayerNames.genericOutLayer)
}
