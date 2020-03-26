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

package com.here.platform.example.location.utils

import au.id.jazzy.play.geojson.{CrsFormat, LineString, NamedCrs}
import com.here.platform.location.core.geospatial.GeoCoordinateOperations.GeoCoordinateOperationsAsMembers
import com.here.platform.location.core.geospatial.{
  GeoCoordinate,
  GeoCoordinateOperations,
  LineStringOperations,
  SinusoidalProjection
}
import play.api.libs.json._

object Visualization {
  class GeoCoordinateFormat[GC: GeoCoordinateOperations] extends CrsFormat[GC] {
    private object UnsupportedReads extends Reads[GC] {
      override def reads(json: JsValue): JsResult[GC] = throw new UnsupportedOperationException
    }

    override val crs = NamedCrs("urn:ogc:def:crs:OGC:1.3:CRS84")
    override def format: Format[GC] =
      Format[GC](this.UnsupportedReads, Writes(gc => Json.arr(gc.longitude, gc.latitude)))
    override val isDefault = true
  }

  implicit def createGeoCoordinateFormat[GC: GeoCoordinateOperations]: GeoCoordinateFormat[GC] =
    new GeoCoordinateFormat[GC]

  import scala.language.implicitConversions

  implicit def createLineString[LS, GC](lineString: LS)(
      implicit lsOps: LineStringOperations[LS]): LineString[GeoCoordinate] = {
    val points = lsOps.points(lineString)
    require(points.size >= 2, "LineString must contain 2 or more points")
    LineString[GeoCoordinate](
      points
        .map { p =>
          import lsOps.PointGeoCoordinateOperations
          p.toLocationGeoCoordinate
        }
        .to[scala.collection.immutable.Seq])
  }

  // simplestyle-spec: https://github.com/mapbox/simplestyle-spec

  def MarkerColor(c: Color): JsObject =
    Json.obj("marker-color" -> c.value)

  def Stroke(c: Color): JsObject =
    Json.obj("stroke" -> c.value)

  sealed abstract class Color(val value: String) extends Product with Serializable
  case object Red extends Color("#e87676")
  case object Green extends Color("#58db58")
  case object Blue extends Color("#76bde8")
  case object Yellow extends Color("#f7f431")
  case object Gray extends Color("#777777")

  private def hex(b: Int): String = "%02X".format(b)

  case class RGB(red: Int, green: Int, blue: Int)
      extends Color(s"#${hex(red)}${hex(green)}${hex(blue)}") {
    def domainConstraint(i: Int): Boolean = i > -1 && i < 256
    require(domainConstraint(red))
    require(domainConstraint(green))
    require(domainConstraint(blue))
  }

  def redToYellowGradient(speed: Float, min: Float = 0f, max: Float = 1f): Color = {
    require(min <= max, s"Min ($min) must be lower than max ($max)")
    val ratio = (speed - min) / (max - min)
    val byteValue = (math.min(math.max(ratio, 0f), 1f) * 255).toInt
    RGB(255, byteValue, 0)
  }

  private val projection = SinusoidalProjection
  def shiftNorthWest(distance: Double)(point: GeoCoordinate): GeoCoordinate = {
    val projected = projection.to(point, point)
    projection.from(point, projected.copy(x = projected.x + distance, y = projected.y + distance))
  }
}
