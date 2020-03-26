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

package com.here.platform.example.location.scala.standalone

import com.here.hrn.HRN
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.integration.optimizedmap.geospatial._
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps

object HereMapContentToOptimizedMapTranslationExample extends App {
  val berlinDowntown = GeoCoordinate(52.5085, 13.3898)
  val radiusInMeters = 50.0

  val catalogFactory = new StandaloneCatalogFactory()

  try {
    val cacheManager = CacheManager.withLruCache()
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)
    val proximitySearch = ProximitySearches.vertices(optimizedMap, cacheManager)

    val hereMapContentToOptimizedMap =
      PropertyMaps.hereMapContentReferenceToVertex(optimizedMap, cacheManager)
    val optimizedMapToHereMapContent =
      PropertyMaps.vertexToHereMapContentReference(optimizedMap, cacheManager)

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
    catalogFactory.terminate()
  }
}
