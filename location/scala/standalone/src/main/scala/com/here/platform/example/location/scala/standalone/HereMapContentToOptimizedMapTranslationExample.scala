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

package com.here.platform.example.location.scala.standalone

import com.here.platform.data.client.base.scaladsl.BaseClient
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.geospatial._
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps

object HereMapContentToOptimizedMapTranslationExample extends App {
  val berlinDowntown = GeoCoordinate(52.5085, 13.3898)
  val radiusInMeters = 50.0

  val baseClient = BaseClient()
  try {
    val optimizedMap = OptimizedMapCatalog
      .from(OptimizedMap.v2.HRN)
      .usingBaseClient(baseClient)
      .newInstance
      .version(1293L)
    val proximitySearch = ProximitySearches(optimizedMap).vertices

    val hereMapContentToOptimizedMap =
      PropertyMaps(optimizedMap).hereMapContentReferenceToVertex
    val optimizedMapToHereMapContent =
      PropertyMaps(optimizedMap).vertexToHereMapContentReference

    proximitySearch
      .search(berlinDowntown, radiusInMeters)
      .take(5)
      .foreach { projection =>
        val hereMapContentReference = optimizedMapToHereMapContent(projection.element)
        val vertex = hereMapContentToOptimizedMap(hereMapContentReference)

        println(s"""|                    Optimized Map vertex: ${projection.element}
                    |Translated to HERE Map Content reference: $hereMapContentReference
                    | Translated back to Optimized Map vertex: $vertex\n""".stripMargin)
      }
  } finally {
    baseClient.shutdown()
  }
}
