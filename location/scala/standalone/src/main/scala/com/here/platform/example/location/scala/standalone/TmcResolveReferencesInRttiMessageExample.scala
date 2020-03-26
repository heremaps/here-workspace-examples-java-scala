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

import java.nio.file.Files

import au.id.jazzy.play.geojson
import com.here.hrn.HRN
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.example.location.utils.Visualization._
import com.here.platform.location.core.graph.PropertyMap
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.referencing.{LinearLocation, LocationReferenceResolvers}
import com.here.platform.location.tpeg2.etl.{ExtendedTMCLocationReference, TMCLocationReference}
import com.here.traffic.realtime.v2.TmcReference._
import com.here.traffic.realtime.v2.Traffic._
import play.api.libs.json._

import scala.collection.immutable

/** Convert and resolve TMC references present in RTTI messages.
  */
object TmcResolveReferencesInRttiMessageExample extends App {
  private val optimizedMapHrn = HRN(
    "hrn:here:data::olp-here:here-optimized-map-for-location-library-2")
  private val optimizedMapVersion = 705L

  private val catalogFactory = new StandaloneCatalogFactory()
  private val optimizedMap = catalogFactory.create(optimizedMapHrn, optimizedMapVersion)
  private val cacheManager = CacheManager.withLruCache()

  private val resolver = LocationReferenceResolvers.extendedTmc(optimizedMap, cacheManager)

  try {
    val rttiMessage =
      TrafficItems.parseFrom(getClass.getClassLoader.getResourceAsStream("rtti-message.bin"))

    val resolvedLocations = rttiMessage.items
      .flatMap(rttiItem => convertTmcReferences(rttiItem))
      .map(tpeg2TmcRef => tpeg2TmcRef -> resolver.resolve(tpeg2TmcRef).location)

    outputResolvedLocations(resolvedLocations)
  } finally {
    catalogFactory.terminate()
  }

  private def convertTmcReferences(rttiItem: TrafficItem): Seq[ExtendedTMCLocationReference] =
    rttiItem.supplementaryLocationRef
      .map { supplementaryLocationReference =>
        supplementaryLocationReference.locationReference
          .flatMap(_.locationReferenceValue.tmcRef)
          .flatMap(_.tmcs)
          .flatMap { rttiTmc =>
            rttiTmc.countryTableCode
              .orElse(supplementaryLocationReference.countryTableCode)
              .map { countryTable =>
                convertTmcReference(countryTable, rttiTmc)
              }
          }
      }
      .getOrElse(Seq.empty)

  private def convertTmcReference(countryTable: CountryTableCode,
                                  tmc: Tmc): ExtendedTMCLocationReference = {
    val (direction, extent, usePrimaryInternal) = tmc.roadwayDirection match {
      case TmcDirection.AT_POSITIVE => (false, 0, true)
      case TmcDirection.APPROACHING_POSITIVE => (false, 1, false)
      case TmcDirection.AT_NEGATIVE => (true, 0, true)
      case TmcDirection.APPROACHING_NEGATIVE => (true, 1, false)
      case _ => sys.error("Unknown roadway direction type")
    }

    ExtendedTMCLocationReference(
      "1.1",
      Some(
        TMCLocationReference(
          locationID = tmc.code.toInt,
          countryCode = Integer.parseInt(countryTable.countryCode, 16).toShort,
          locationTableNumber = countryTable.tableId.toShort,
          direction = direction,
          bothDirections = false,
          extent = Some(extent.toShort),
          extendedCountryCode = Some(Integer.parseInt(countryTable.extendedCountryCode, 16).toShort),
          locationTableVersion = None,
          preciseTMCInfo = None,
          useInternalPrimaryLocation = usePrimaryInternal,
          useInternalSecondaryLocation = false
        )),
      None
    )
  }

  private def outputResolvedLocations(
      resolvedLocations: Seq[(ExtendedTMCLocationReference, LinearLocation)]): Unit = {
    println("Resolved locations:")
    resolvedLocations.foreach {
      case (ref, location) =>
        println(s"$ref: $location")
    }

    val geometries: PropertyMap[Vertex, PackedLineString] =
      PropertyMaps.geometry(optimizedMap, cacheManager)

    val allResolvedVertices = resolvedLocations.flatMap {
      case (ref, location) =>
        location.path.map(vertex => vertex -> ref)
    }.distinct

    val geoJsonFeatures = allResolvedVertices.map {
      case (vertex, ref) =>
        val properties = Stroke(Blue) +
          ("vertex" -> JsString(s"${vertex.tileId.value}:${vertex.index.value}")) +
          ("locationCode" -> JsNumber(ref.tmcLocation.get.locationID))
        geojson.Feature(geometries(vertex), Some(properties))
    }

    val json = Json.toJson(geojson.FeatureCollection(geoJsonFeatures.to[immutable.Seq]))
    val path = FileNameHelper.exampleJsonFileFor(TmcResolveReferencesInRttiMessageExample).toPath
    Files.write(path, Json.prettyPrint(json).getBytes)
    println(s"""
               |A GeoJson representation of the resolved vertices is available in $path
               |""".stripMargin)
  }
}
