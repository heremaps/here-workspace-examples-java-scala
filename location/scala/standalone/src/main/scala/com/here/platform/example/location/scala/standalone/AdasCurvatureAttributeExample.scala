/*
 * Copyright (C) 2017-2026 HERE Europe B.V.
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
import com.here.platform.example.location.scala.standalone.utils.FileNameHelper
import com.here.platform.location.core.geospatial.{GeoCoordinate, LineString, LineStrings}
import com.here.platform.location.core.graph.DirectedGraph
import com.here.platform.location.core.mapmatching.MatchedPath.Transition
import com.here.platform.location.core.mapmatching.{MatchResult, MatchedPath, OnRoad}
import com.here.platform.location.inmemory.graph.{Edge, Vertex}
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.graph.{Graphs, PropertyMaps}
import com.here.platform.location.integration.optimizedmap.mapmatching.PathMatchers
import com.here.platform.location.integration.optimizedmap.{OptimizedMap, OptimizedMapLayers}
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}

import java.io.FileOutputStream

object AdasCurvatureAttributeExample extends App {
  val baseClient = BaseClient()

  try {
    val optimizedMap = OptimizedMapCatalog
      .from(OptimizedMap.v2.HRN)
      .usingBaseClient(baseClient)
      .newInstance
      .version(7647)

    val vertices = verticesFromPath(optimizedMap)(
      Seq(
        GeoCoordinate(46.517259, 10.320718),
        GeoCoordinate(46.515857, 10.317518),
        GeoCoordinate(46.517532, 10.315887),
        GeoCoordinate(46.516155, 10.313913),
        GeoCoordinate(46.515014, 10.315184),
        GeoCoordinate(46.514859, 10.316852),
        GeoCoordinate(46.513499, 10.318810)
      ))

    val propertyMaps = PropertyMaps(optimizedMap)

    val adas = propertyMaps.adasAttributes

    val graph: DirectedGraph[Vertex, Edge] = Graphs(optimizedMap).forward

    val edges: Iterator[Edge] =
      vertices.sliding(2).flatMap {
        case Seq(source, target) =>
          graph.edgeIterator(source, target)
      }

    def toColor(curvature: Int): Color = {
      // Converting curvature value to radius in meters, see:
      // https://www.here.com/docs/bundle/map-content-specifications-data-specification/page/topics_schema/curvature-attribute.html
      val radius = Math.abs(1000000.0 / curvature)
      // Gradient from red to green depending on road curvature radius, considering as green all radius above 150 meters.
      Color.hsb(Math.min(radius, 150.0), 0.9, 0.8)
    }

    val geometry = propertyMaps.geometry

    val routeAsFeature = vertices.map { vertex =>
      Feature.lineString(geometry(vertex),
                         SimpleStyleProperties().strokeWidth(10).stroke(Color.Gray))
    }

    val curvaturePointsAsFeature = vertices.flatMap { vertex =>
      Feature.lineStringPoints(geometry(vertex), adas.curvature(vertex))(
        pointBasedProperty =>
          SimpleStyleProperties()
            .markerColor(toColor(pointBasedProperty.value))
            .markerSize("small"))
    }

    val length = propertyMaps.length

    def lineStringPart(vertex: Vertex, meters: Double): LineString[GeoCoordinate] = {
      val fraction = meters / Math.max(length(vertex), Math.abs(meters))
      LineStrings
        .cut(geometry(vertex),
             if (fraction >= 0) Seq(0.0 -> fraction)
             else Seq(1.0 + fraction -> 1.0))
        .head
    }

    val edgesAsFeature = edges.map { edge =>
      Feature.lineString(
        LineString(
          lineStringPart(graph.source(edge), -10.0).points ++ lineStringPart(graph.target(edge),
                                                                             10.0).points),
        SimpleStyleProperties()
          .stroke(adas.edgeCurvature(edge).map(toColor).getOrElse(Color.Black))
          .strokeWidth(6)
      )
    }

    val path = FileNameHelper.exampleJsonFileFor(this)
    val fos = new FileOutputStream(path)

    try {
      FeatureCollection(routeAsFeature ++ curvaturePointsAsFeature ++ edgesAsFeature)
        .writePretty(fos)
    } finally {
      fos.close()
    }

    println(s"\nA GeoJson representation of the result is available in $path\n")
  } finally {
    baseClient.shutdown()
  }

  private def verticesFromPath(optimizedMap: OptimizedMapLayers)(
      path: Seq[GeoCoordinate]): Seq[Vertex] = {
    val MatchedPath(results, transitions) = PathMatchers(optimizedMap)
      .carPathMatcherWithTransitions[GeoCoordinate]
      .matchPath(path)

    require(results.nonEmpty && results.forall(_.isInstanceOf[OnRoad[_]]))
    def vertex(result: MatchResult[Vertex]): Option[Vertex] = result match {
      case OnRoad(ep) => Some(ep.element)
      case _ => None
    }

    (vertex(results.head).get +: transitions.flatMap {
      case Transition(_, to, transition) => transition :+ vertex(results(to)).get
    }).distinct
  }
}
