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

import java.io.InputStreamReader
import java.nio.file.Files

import com.github.tototoshi.csv.CSVReader
import com.here.hrn.HRN
import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.location.core.geospatial.{
  ElementProjection,
  GeoCoordinate,
  ProximitySearch
}
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.heremapcontent.geospatial.Implicits.HereCommonPointOps
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
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
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)

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
    def loadTripFromCSVResource(s: String): Seq[Point] =
      CSVReader.open(new InputStreamReader(getClass.getResourceAsStream(s))).all.map {
        case List(lat, lon) => new Point(lat.toDouble, lon.toDouble)
      }

    def serializeToGeoJson(probePoints: Seq[Point],
                           matches: Seq[Option[ElementProjection[Vertex]]],
                           optimizedMap: Catalog,
                           cacheManager: CacheManager): Unit = {
      def toGC(point: Point): GeoCoordinate = HereCommonPointOps.toLocationGeoCoordinate(point)

      import au.id.jazzy.play.geojson
      import au.id.jazzy.play.geojson._
      import com.here.platform.example.location.utils.Visualization._
      import com.here.platform.location.core.geospatial
      import play.api.libs.json._

      import scala.collection.immutable

      val matchedPointsAsFeatures: immutable.Seq[Feature[GeoCoordinate]] = matches
        .zip(probePoints)
        .flatMap {
          case (Some(ep), pp) =>
            Seq(
              Feature(geojson.Point(toGC(pp)), Some(MarkerColor(Red))),
              Feature(geojson.Point(ep.nearest), Some(MarkerColor(Green))),
              Feature(geospatial.LineString(Seq(toGC(pp), ep.nearest)), Some(Stroke(Blue)))
            )
          case (None, pp) => Seq(Feature(geojson.Point(toGC(pp)), Some(JsObject.empty)))
        }
        .to[immutable.Seq]

      val json =
        Json.toJson(FeatureCollection(matchedPointsAsFeatures))
      val path = FileNameHelper.exampleJsonFileFor(PointMatcherExample).toPath
      Files.write(path, Json.prettyPrint(json).getBytes)
      println("\nA GeoJson representation of the result is available in " + path + "\n")
    }
  }
}
