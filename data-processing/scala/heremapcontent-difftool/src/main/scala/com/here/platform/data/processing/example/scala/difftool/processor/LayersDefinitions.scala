/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

package com.here.platform.data.processing.example.scala.difftool.processor

import com.here.platform.data.processing.catalog.Catalog.Id
import com.here.platform.data.processing.catalog.{Catalog, Layer}
import com.here.platform.data.processing.driver.Default

object LayersDefinitions {
  trait LayerDefinitions {
    val inLayers = Map(
      In.Rib -> Set(In.LayerName),
      In.PreviousRib -> Set(In.LayerName)
    )

    val outLayers = Set(Out.LayerName)
  }

  object In {
    val Rib = Catalog.Id("rib")
    val PreviousRib: Id = Default.PreviousRunId(Rib)
    val LayerName = Layer.Id("topology-geometry")
  }

  object Out {
    val LayerName = Layer.Id("topology-geometry-diff")
  }
}
