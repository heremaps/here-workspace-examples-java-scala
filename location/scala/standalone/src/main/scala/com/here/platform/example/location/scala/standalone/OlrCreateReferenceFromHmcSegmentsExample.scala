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
import com.here.platform.location.compilation.heremapcontent.TopologyAttributeDescription
import com.here.platform.location.core.geospatial.GeoCoordinate
import com.here.platform.location.core.mapmatching.MatchedPath.Transition
import com.here.platform.location.core.mapmatching.{MatchResult, MatchedPath, OnRoad}
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.optimizedmap.dcl2.OptimizedMapCatalog
import com.here.platform.location.integration.optimizedmap.mapmatching.PathMatchers
import com.here.platform.location.integration.optimizedmap.{OptimizedMap, OptimizedMapLayers}
import com.here.platform.location.referencing.olr.OlrPrettyPrinter
import com.here.platform.location.referencing.{LinearLocation, LocationReferenceCreators}
import com.here.platform.location.tpeg2.olr.OpenLRLocationReference
import com.here.platform.location.tpeg2.{BinaryMarshallers, XmlMarshallers}

import java.io.FileOutputStream

/** This example shows how to take a path given as HERE Map Content references
  * and create an OLR reference from it.
  */
object OlrCreateReferenceFromHmcSegmentsExample extends App {
  val path = Seq(
    GeoCoordinate(52.526323, 13.368411),
    GeoCoordinate(52.526055, 13.367710),
    GeoCoordinate(52.525940, 13.367385),
    GeoCoordinate(52.525815, 13.366990),
    GeoCoordinate(52.525677, 13.366529),
    GeoCoordinate(52.525492, 13.365993),
    GeoCoordinate(52.524971, 13.364546),
    GeoCoordinate(52.524525, 13.363130),
    GeoCoordinate(52.524420, 13.362815),
    GeoCoordinate(52.524237, 13.362253),
    GeoCoordinate(52.523935, 13.361275),
    GeoCoordinate(52.523740, 13.360635),
    GeoCoordinate(52.523589, 13.360122),
    GeoCoordinate(52.523582, 13.358712),
    GeoCoordinate(52.523705, 13.357395),
    GeoCoordinate(52.523735, 13.356985),
    GeoCoordinate(52.523660, 13.356810),
    GeoCoordinate(52.523216, 13.356674),
    GeoCoordinate(52.521860, 13.356260),
    GeoCoordinate(52.520269, 13.355715),
    GeoCoordinate(52.519508, 13.355407),
    GeoCoordinate(52.519186, 13.355312),
    GeoCoordinate(52.518710, 13.355185),
    GeoCoordinate(52.518235, 13.355025),
    GeoCoordinate(52.517683, 13.354852),
    GeoCoordinate(52.517210, 13.354645),
    GeoCoordinate(52.516859, 13.354292),
    GeoCoordinate(52.516293, 13.353042),
    GeoCoordinate(52.515802, 13.351872),
    GeoCoordinate(52.515331, 13.351070),
    GeoCoordinate(52.515166, 13.350226),
    GeoCoordinate(52.515141, 13.349736),
    GeoCoordinate(52.514928, 13.349263),
    GeoCoordinate(52.514695, 13.349055),
    GeoCoordinate(52.514412, 13.349066),
    GeoCoordinate(52.514060, 13.349293),
    GeoCoordinate(52.513897, 13.349654),
    GeoCoordinate(52.513855, 13.349940),
    GeoCoordinate(52.512909, 13.350431),
    GeoCoordinate(52.511146, 13.350882),
    GeoCoordinate(52.510010, 13.351145),
    GeoCoordinate(52.509574, 13.350733),
    GeoCoordinate(52.509402, 13.350056),
    GeoCoordinate(52.508974, 13.349522),
    GeoCoordinate(52.507996, 13.348989),
    GeoCoordinate(52.507321, 13.348056),
    GeoCoordinate(52.506886, 13.346848),
    GeoCoordinate(52.506658, 13.345701),
    GeoCoordinate(52.506535, 13.345075),
    GeoCoordinate(52.506234, 13.343588),
    GeoCoordinate(52.505904, 13.341960),
    GeoCoordinate(52.505485, 13.341235),
    GeoCoordinate(52.505235, 13.341035),
    GeoCoordinate(52.505368, 13.340400),
    GeoCoordinate(52.505356, 13.339351),
    GeoCoordinate(52.505198, 13.338267),
    GeoCoordinate(52.505286, 13.335972),
    GeoCoordinate(52.505549, 13.333957),
    GeoCoordinate(52.505935, 13.333118),
    GeoCoordinate(52.506275, 13.332450),
    GeoCoordinate(52.506569, 13.331741),
    GeoCoordinate(52.507877, 13.332180)
  )

  val baseClient = BaseClient()

  try {
    val optimizedMap: OptimizedMapLayers =
      OptimizedMapCatalog
        .from(OptimizedMap.v2.HRN)
        .usingBaseClient(baseClient)
        // Retain OLR attributes.
        // See https://docs.here.com/workspace/docs/ll-docs-high-level-v2#retain-only-required-attributes
        .withTopologyAttributes(
          TopologyAttributeDescription.RoadUsage,
          TopologyAttributeDescription.FunctionalClass,
          TopologyAttributeDescription.PhysicalAttribute,
          TopologyAttributeDescription.SpecialTrafficAreaCategory
        )
        .newInstance
        .version(7521L)

    val vertices = verticesFromPath(optimizedMap)(path)

    val location = LinearLocation(vertices)

    val creator = LocationReferenceCreators(optimizedMap).olrLinear

    val reference = creator.create(location)

    // For debugging purposes there is a prettyPrint function.
    println(OlrPrettyPrinter.prettyPrint(reference))

    // This is how to serialize the reference to XML.
    XmlMarshallers.openLRLocationReference
      .marshall(OpenLRLocationReference("1.1", reference, None), new FileOutputStream("olr.xml"))

    // This is how to serialize the reference to binary.
    BinaryMarshallers.openLRLocationReference
      .marshall(OpenLRLocationReference("1.1", reference, None), new FileOutputStream("olr.bin"))
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
