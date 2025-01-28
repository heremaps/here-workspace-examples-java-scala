/*
 * Copyright (C) 2017-2025 HERE Europe B.V.
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
import com.here.platform.location.inmemory.graph.{Backward, Edge, Forward, Vertex}
import com.here.platform.location.integration.heremapcontent.PartitionId
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.adasattributes.CurvatureHeading
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.geospatial.{
  SegmentId,
  HereMapContentReference => HMCRef
}
import com.here.platform.location.integration.optimizedmap.graph.{Graphs, PropertyMaps}
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{
  Feature,
  FeatureCollection,
  SimpleStyleProperties
}

import java.io.FileOutputStream

object AdasCurvatureAttributeExample extends App {
  val segments = Seq(
    HMCRef(PartitionId("23598867"), SegmentId("here:cm:segment:154024123"), Forward),
    HMCRef(PartitionId("23598867"), SegmentId("here:cm:segment:150551733"), Backward),
    HMCRef(PartitionId("23598867"), SegmentId("here:cm:segment:76960691"), Backward),
    HMCRef(PartitionId("23598867"), SegmentId("here:cm:segment:150552074"), Forward),
    HMCRef(PartitionId("23598867"), SegmentId("here:cm:segment:98035021"), Backward),
    HMCRef(PartitionId("23598867"), SegmentId("here:cm:segment:87560942"), Forward)
  )

  val baseClient = BaseClient()

  try {
    val optimizedMap = OptimizedMapCatalog
      .from(OptimizedMap.v2.HRN)
      .usingBaseClient(baseClient)
      .newInstance
      .version(1126)

    val propertyMaps = PropertyMaps(optimizedMap)
    val hmcToVertex = propertyMaps.hereMapContentReferenceToVertex

    val vertices = segments.map(hmcToVertex(_))

    val adas = propertyMaps.adasAttributes

    val graph: DirectedGraph[Vertex, Edge] = Graphs(optimizedMap).forward

    val edges: Iterator[Edge] =
      vertices.sliding(2).flatMap {
        case Seq(source, target) =>
          graph.edgeIterator(source, target)
      }

    def toColor(value: CurvatureHeading): Color = {
      // Converting curvature value to radius in meters, see:
      // https://developer.here.com/documentation/here-map-content/dev_guide/topics-attributes/curvature.html
      val radius = Math.abs(1000000.0 / value.curvature)
      // Gradient from red to green depending on road curvature radius, considering as green all radius above 150 meters.
      Color.hsb(Math.min(radius, 150.0), 0.9, 0.8)
    }

    val geometry = propertyMaps.geometry

    val routeAsFeature = vertices.map { vertex =>
      Feature.lineString(geometry(vertex),
                         SimpleStyleProperties().strokeWidth(10).stroke(Color.Gray))
    }

    val curvaturePointsAsFeature = vertices.flatMap { vertex =>
      Feature.lineStringPoints(geometry(vertex), adas.curvatureHeading(vertex))(
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
          .stroke(adas.edgeCurvatureHeading(edge).map(toColor).getOrElse(Color.Black))
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
}
