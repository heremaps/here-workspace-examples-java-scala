/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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

package com.here.platform.data.processing.example.scala.feedback

import com.here.platform.data.processing.catalog.{Catalog, Layer}
import com.here.platform.data.processing.driver.Default

/**
  * Input and output layers configuration for the stateful compiler
  */
trait LayerCatalogDefs {
  def inLayers: Map[Catalog.Id, Set[Layer.Id]] = Map(
    CatalogDefs.InputCatalog -> Set(LayersDefs.topologyGeometry),
    // return current base version of the output catalog(output produced from the previous run)
    Default.FeedbackCatalogId -> Set(LayersDefs.nodeCardinality)
  )

  def outLayers: Set[Layer.Id] = Set(LayersDefs.nodeCardinality)
}

/**
  * Input catalog definitions
  */
object CatalogDefs {
  // The input catalog ID must match the corresponding entry in the input-catalogs section of pipeline-config.conf
  val InputCatalog = Catalog.Id("rib")
}

/**
  * Input and output layer definitions
  */
object LayersDefs {
  val topologyGeometry = Layer.Id("topology-geometry")
  val nodeCardinality = Layer.Id("nodecardinality-count")
}
