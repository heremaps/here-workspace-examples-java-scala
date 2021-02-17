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

package com.here.platform.example.location.scala.standalone

import java.io.ByteArrayInputStream

import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.{Backward, Forward}
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.geospatial.HereMapContentReference
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.referencing.{LinearLocation, LocationReferenceResolvers}
import com.here.platform.location.tpeg2.XmlMarshallers

/** This example shows how to take an OLR reference given in XML
  * and to resolve this reference to HERE Map Content references.
  */
object OlrResolveReferenceToHmcSegmentsExample extends App {
  val olrReference =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<olr:OpenLRLocationReference xmlns:olr="http://www.tisa.org/TPEG/OLR_0_1">
      |    <olr:version>1.1</olr:version>
      |    <olr:locationReference>
      |        <olr:optionLinearLocationReference>
      |            <olr:first>
      |                <olr:coordinate>
      |                    <olr:longitude>623039</olr:longitude>
      |                    <olr:latitude>2447911</olr:latitude>
      |                </olr:coordinate>
      |                <olr:lineProperties>
      |                    <olr:frc olr:table="olr001_FunctionalRoadClass" olr:code="2"></olr:frc>
      |                    <olr:fow olr:table="olr002_FormOfWay" olr:code="2"></olr:fow>
      |                    <olr:bearing>
      |                        <olr:value>174</olr:value>
      |                    </olr:bearing>
      |                </olr:lineProperties>
      |                <olr:pathProperties>
      |                    <olr:lfrcnp olr:table="olr001_FunctionalRoadClass" olr:code="1"></olr:lfrcnp>
      |                    <olr:dnp>
      |                        <olr:value>4649</olr:value>
      |                    </olr:dnp>
      |                    <olr:againstDrivingDirection>false</olr:againstDrivingDirection>
      |                </olr:pathProperties>
      |            </olr:first>
      |            <olr:last>
      |                <olr:coordinate>
      |                    <olr:longitude>-3598</olr:longitude>
      |                    <olr:latitude>-1748</olr:latitude>
      |                </olr:coordinate>
      |                <olr:lineProperties>
      |                    <olr:frc olr:table="olr001_FunctionalRoadClass" olr:code="1"></olr:frc>
      |                    <olr:fow olr:table="olr002_FormOfWay" olr:code="3"></olr:fow>
      |                    <olr:bearing>
      |                        <olr:value>144</olr:value>
      |                    </olr:bearing>
      |                </olr:lineProperties>
      |            </olr:last>
      |        </olr:optionLinearLocationReference>
      |    </olr:locationReference>
      |</olr:OpenLRLocationReference>
      |""".stripMargin

  val catalogFactory = new StandaloneCatalogFactory()

  try {
    val cacheManager = CacheManager.withLruCache()
    val optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 769L)

    val reference = XmlMarshallers.openLRLocationReference
      .unmarshall(new ByteArrayInputStream(olrReference.getBytes("utf-8")))

    val resolver = LocationReferenceResolvers.olr(optimizedMap, cacheManager)

    val location = resolver.resolve(reference)

    val vertexToHmc = PropertyMaps.vertexToHereMapContentReference(optimizedMap, cacheManager)

    // OLR supports multiple types of location references.
    // If we use the universal OLR resolver (olr(…)), we need to
    // check which subtype of `ReferencingLocation` we actually get back.
    location match {
      case linearLocation: LinearLocation =>
        println(
          linearLocation.path.map(vertexToHmc(_)).map(toHmcRefString).mkString("\n")
        )
      case _ => println("This example only deals with LinearLocations.")
    }
  } finally {
    catalogFactory.terminate()
  }

  def toHmcRefString(hmcRef: HereMapContentReference): String =
    s"${hmcRef.partitionId.value}/${hmcRef.segmentId.value.stripPrefix("here:cm:segment:")}${hmcRef.direction match {
      case Forward => '+'
      case Backward => '-'
    }}"
}
