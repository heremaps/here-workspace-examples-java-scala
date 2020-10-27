/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * The following rights to redistribution and use the software example in
 * source and binary forms, with or without modification, are granted to
 * you under copyrights provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * For clarification, no licenses to any patents are granted under this license.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  SPDX-License-Identifier: BSD-3-Clause
 */

package com.here.platform.example.location.scala.standalone

import com.here.hrn.HRN
import java.io.FileOutputStream

import com.here.platform.example.location.utils.FileNameHelper
import com.here.platform.location.core.Utils.normalizeLongitude
import com.here.platform.location.core.geospatial.Implicits._
import com.here.platform.location.core.geospatial.{
  GeoCoordinate,
  GeoCoordinates,
  LineStringOperations
}
import com.here.platform.location.core.graph.{
  DirectedGraph,
  FilteredGraph,
  PropertyMap,
  RangeBasedPropertyMap
}
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.geospatial.PackedLineString
import com.here.platform.location.inmemory.graph.{Edge, Vertex}
import com.here.platform.location.integration.optimizedmap.geospatial.ProximitySearches
import com.here.platform.location.integration.optimizedmap.graph.{Graphs, PropertyMaps, RoadAccess}
import com.here.platform.location.integration.optimizedmap.roadattributes.FunctionalClass
import com.here.platform.location.io.scaladsl.Color
import com.here.platform.location.io.scaladsl.geojson.{FeatureCollection, SimpleStyleProperties}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq, Seq}

// Determine the Most Probable Path from a starting vertex.
//
// This example builds on concepts of the US6405128 patent and on the HERE ADAS Research platform.
//
// A "most probable path" is identified following a greedy rule considering:
//   1. Change in functional class
//   2. Angle between roads
//
// The algorithm is stateless and will usually not be able to exit roundabouts, nonetheless it
// should provide ~87% accuracy on a turn by turn base.
//
object MostProbablePathExample extends App {
  def mostProbablePath(startingVertex: Vertex,
                       maximumDistanceInMeters: Double,
                       graph: DirectedGraph[Vertex, Edge],
                       lengthPropertyMap: PropertyMap[Vertex, Double],
                       probabilityStrategy: (IndexedSeq[Vertex], Vertex) => Double): Seq[Vertex] = {
    @tailrec
    def mppAcc(path: Vector[Vertex], accumulatedLength: Double): (Seq[Vertex], Double) =
      if (accumulatedLength < maximumDistanceInMeters) {
        graph.outEdgeIterator(path.last).map(graph.target) match {
          case vertices if vertices.isEmpty => (path, accumulatedLength)
          case vertices =>
            val next = vertices.maxBy(w => probabilityStrategy(path, w))
            mppAcc(path :+ next, accumulatedLength + lengthPropertyMap(next))
        }
      } else
        (path, accumulatedLength)

    mppAcc(Vector(startingVertex), lengthPropertyMap(startingVertex))._1
  }

  def filterBy(
      graph: DirectedGraph[Vertex, Edge],
      booleanRangeBasedProperty: RangeBasedPropertyMap[Vertex, Boolean])(e: Edge): Boolean =
    checkBooleanRangedProperty(graph.target(e), booleanRangeBasedProperty)

  def checkBooleanRangedProperty(v: Vertex, propertyMap: RangeBasedPropertyMap[Vertex, Boolean]) = {
    val ranges = propertyMap(v)
    ranges.nonEmpty && ranges.forall(_.value)
  }

  // get the weighted probability of adding `next` to the current path
  def probability[LS: LineStringOperations](
      functionalClassMap: RangeBasedPropertyMap[Vertex, FunctionalClass],
      geometryMap: PropertyMap[Vertex, LS])(current: IndexedSeq[Vertex], next: Vertex): Double =
    0.5 * turnAngleProbability(current.last, next, geometryMap) +
      0.5 * functionalClassProbability(current.last, next, functionalClassMap)

  def validFunctionalClass(fc: Int) = fc >= 1 && fc <= 5

  def functionalClassProbability(
      current: Vertex,
      next: Vertex,
      functionalClass: RangeBasedPropertyMap[Vertex, FunctionalClass]): Double = {
    // Functional Class at the end (1.0) of the current link
    val fcCurrent = functionalClass(current, 1.0).get.value.value
    // Functional Class at the start (0.0) of the next link
    val fcNext = functionalClass(next, 0.0).get.value.value
    if (!validFunctionalClass(fcCurrent) || !validFunctionalClass(fcNext)) return 0.0
    fcCurrent - fcNext match {
      case x if x <= -2 => 0.1
      case -1 => 0.2
      case 0 => 0.5
      case 1 => 0.7
      case _ => 1.0
    }
  }

  def turnAngleProbability[LS: LineStringOperations](
      current: Vertex,
      next: Vertex,
      geometryPropertyMap: PropertyMap[Vertex, LS]): Double = {
    val endOfCurrent = geometryPropertyMap(current).points.takeRight(4)
    val headingCurrent = GeoCoordinates.heading(endOfCurrent.head, endOfCurrent.last)

    val startOfNext = geometryPropertyMap(next).points.take(4)
    val headingNext = GeoCoordinates.heading(startOfNext.head, startOfNext.last)

    val deltaAngle = normalizeLongitude(headingNext - headingCurrent)

    1.0 - math.abs(deltaAngle) / 180.0
  }

  val catalogFactory = new StandaloneCatalogFactory()

  try {
    val cacheManager = CacheManager.withLruCache()
    val optimizedMap =
      catalogFactory.create(
        HRN("hrn:here:data::olp-here:here-optimized-map-for-location-library-2"),
        705L)

    // A mapping from Vertices to LineString of the underlying road geometry
    val geometryPropertyMap: PropertyMap[Vertex, PackedLineString] =
      PropertyMaps.geometry(optimizedMap, cacheManager)

    // The length of a Vertex (road segment)
    val lengthPropertyMap: PropertyMap[Vertex, Double] =
      PropertyMaps.length(optimizedMap, cacheManager)

    val roadAttributes = PropertyMaps.RoadAttributes(optimizedMap, cacheManager)

    val accessibleByCarPropertyMap: RangeBasedPropertyMap[Vertex, Boolean] =
      PropertyMaps.roadAccess(optimizedMap, cacheManager, RoadAccess.Automobile)

    // Load the graph and apply a filter to only navigate links that are accessible by cars
    val filteredGraph =
      new FilteredGraph(
        Graphs.from(optimizedMap, cacheManager),
        filterBy(Graphs.from(optimizedMap, cacheManager), accessibleByCarPropertyMap))

    val startPoint = GeoCoordinate(52.50201, 13.37548)
    val searchRadiusInMeters = 30.0

    // Select a starting drivable Vertex
    val start = ProximitySearches
      .vertices(optimizedMap, cacheManager)
      .search(
        startPoint,
        searchRadiusInMeters
      )
      .filter(e => checkBooleanRangedProperty(e.element, accessibleByCarPropertyMap))
      .minBy(_.distanceInMeters)
      .element

    val probabilityFunction: (IndexedSeq[Vertex], Vertex) => Double =
      probability(roadAttributes.functionalClass, geometryPropertyMap)
    val maximumMppLengthInMeters = 10000.0
    val mostProbablePathSegments = mostProbablePath(start,
                                                    maximumMppLengthInMeters,
                                                    filteredGraph,
                                                    lengthPropertyMap,
                                                    probabilityFunction)

    // Write the results in GeoJSON format
    val mostProbablePathJson =
      mostProbablePathSegments
        .foldLeft(FeatureCollection()) { (fc, vertexItem) =>
          val Yellow = Color("#f7f431")
          fc.lineString(geometryPropertyMap(vertexItem), SimpleStyleProperties().stroke(Yellow))
        }

    val path = FileNameHelper.exampleJsonFileFor(MostProbablePathExample)

    val fos = new FileOutputStream(path)
    mostProbablePathJson.writePretty(fos)
    fos.close()

    println("\nA GeoJson representation of the result is available in:\n" + path + "\n")
  } finally {
    catalogFactory.terminate()
  }
}
