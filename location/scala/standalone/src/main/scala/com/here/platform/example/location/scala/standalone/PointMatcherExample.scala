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

import java.io.{File, FileOutputStream, InputStreamReader}

import com.github.tototoshi.csv.CSVReader
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.location.core.geospatial.Implicits._
import com.here.platform.location.core.geospatial.{ElementProjection, LineString, ProximitySearch}
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.heremapcontent.geospatial.Implicits.HereCommonPointOps
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{FeatureCollection, SimpleStyleProperties}
import com.here.schema.geometry.v2.geometry.Point

/**
  * A point matcher based on the Location Library.
  */
object PointMatcherExample extends App {
  import Helpers._

  /**
    * Given a trip, returns the matched points on the unrestricted road network.
    *
    * A trip is a sequence of probe points.
    *
    * For the sake of simplicity we do not take into consideration neither the heading
    * nor the direction of travel, so the result might be a Vertex that is not
    * traversable.
    */
  def matchTrip(ps: ProximitySearch[Vertex],
                trip: Seq[Point],
                radiusInMeters: Double): Seq[Option[ElementProjection[Vertex]]] =
    trip.map(point =>
      ps.search(point, radiusInMeters) match {
        case result if result.isEmpty => None
        case result => Some(result.minBy(_.distanceInMeters))
      })

  val catalogFactory = new StandaloneCatalogFactory()

  try {
    val cacheManager = CacheManager.withLruCache()
    val optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 1293L)

    val trip: Seq[Point] = loadTripFromCSVResource("/example_berlin_path.csv")
    println(s"Loaded trip with ${trip.length} points.")

    val RadiusInMeters = 10.0

    val proximitySearch = ProximitySearches.vertices(optimizedMap, cacheManager)

    val matchedPoints: Seq[Option[ElementProjection[Vertex]]] =
      matchTrip(proximitySearch, trip, RadiusInMeters)
    assert(matchedPoints.flatten.nonEmpty)

    serializeToGeoJson(trip, matchedPoints, optimizedMap, cacheManager)
  } finally {
    catalogFactory.terminate()
  }

  object Helpers {
    val Red = Color("#e87676")
    val Green = Color("#58db58")
    val Blue = Color("#76bde8")

    def loadTripFromCSVResource(s: String): Seq[Point] =
      CSVReader.open(new InputStreamReader(getClass.getResourceAsStream(s))).all.map {
        case List(lat, lon) => new Point(lat.toDouble, lon.toDouble)
      }

    def serializeToGeoJson(probePoints: Seq[Point],
                           matches: Seq[Option[ElementProjection[Vertex]]],
                           optimizedMap: Catalog,
                           cacheManager: CacheManager): Unit = {
      val matchedPointsAsFeatureCollection = matches
        .zip(probePoints)
        .foldLeft(FeatureCollection()) {
          case (fc, (Some(ep), pp)) =>
            fc.point(pp, SimpleStyleProperties().markerColor(Red))
              .point(ep.nearest, SimpleStyleProperties().markerColor(Green))
              .lineString(LineString(Seq(pp.toLocationGeoCoordinate, ep.nearest)),
                          SimpleStyleProperties().stroke(Blue))
          case (fc, (None, pp)) => fc.point(pp, SimpleStyleProperties())
        }

      val geojsonFile: File = FileNameHelper.exampleJsonFileFor(PointMatcherExample)
      val fos = new FileOutputStream(geojsonFile)
      matchedPointsAsFeatureCollection.writePretty(fos)
      fos.close()

      println(s"\nA GeoJson representation of the result is available in $geojsonFile\n")
    }
  }
}
