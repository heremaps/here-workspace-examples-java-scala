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

package com.here.platform.example.location.scala.spark

import akka.actor.ActorSystem
import au.id.jazzy.play.geojson
import au.id.jazzy.play.geojson.Feature
import com.here.hrn.HRN
import com.here.platform.data.client.engine.scaladsl.DataEngine
import com.here.platform.data.client.scaladsl.{CommitPartition, DataClient, NewPartition}
import com.here.platform.data.client.spark.DataClientSparkContextUtils
import com.here.platform.data.client.spark.LayerDataFrameReader._
import com.here.platform.data.client.spark.SparkSupport._
import com.here.platform.example.location.utils.Visualization._
import com.here.platform.location.core.geospatial._
import com.here.platform.location.core.mapmatching.OnRoad
import com.here.platform.location.dataloader.core.Catalog
import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.spark.SparkCatalogFactory
import com.here.platform.location.inmemory.geospatial.TileId
import com.here.platform.location.inmemory.graph.Vertex
import com.here.platform.location.integration.herecommons.geospatial.{
  HereTileLevel,
  HereTileResolver
}
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.integration.optimizedmap.mapmatching.PathMatchers
import com.here.platform.location.spark.{Cluster, DistributedClustering}
import com.here.platform.pipeline.PipelineContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.language.postfixOps

object InferStopSignsFromSensorsExample {
  // For Apache Spark applications, you have to initialize the cacheManager as a member of a singleton object
  // to ensure that only one cacheManager is used per executor.
  private val cacheManager = CacheManager.withLruCache()

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    import Steps._

    val config = Config(new PipelineContext)

    val sparkSession =
      SparkSession.builder().appName(this.getClass.getSimpleName.takeWhile('$'.!=)).getOrCreate()

    val catalogFactory = new SparkCatalogFactory

    val optimizedMap =
      catalogFactory.create(config.optimizedMapCatalogHrn, config.optimizedMapCatalogVersion)

    // Extracting input data

    val sdiiMessages: DataFrame = sparkSession
      .readLayer(config.sdiiCatalogHrn, config.sdiiLayer)
      .query(s"hour_from>0")
      .load()

    import sparkSession.implicits._

    // Each stop sign event consists of the event position and the path that a vehicle drove along immediately before
    // and after the time the event happened. We call such a path a `path around event`. `Around event` in this case
    // refers to temporal, not spatial vicinity.
    //
    // Actually, the data that we receive from the SDII catalog does not provide us with event positions but time stamps
    // when the events occurred. The `extractStopSignEventFromSdiiMessage` user defined function applies interpolation
    // logic to derive event positions i.e. to guess what was the position at the time when the event happened.
    val stopSignEvents: Dataset[StopSignEvent] = sdiiMessages
      .select(
        $"path.positionEstimate.latitude_deg" as "latitude",
        $"path.positionEstimate.longitude_deg" as "longitude",
        $"path.positionEstimate.timeStampUTC_ms" as "timestamp",
        explode($"pathEvents.signRecognition") as "signEvent"
      )
      .where($"signEvent.roadSignType" === "STOP_SIGN")
      .select($"latitude",
              $"longitude",
              $"timestamp",
              $"signEvent.timeStampUTC_ms" as "eventTimestamp")
      .select(
        extractStopSignEventFromSdiiMessage($"latitude",
                                            $"longitude",
                                            $"timestamp",
                                            $"eventTimestamp") as "stopSignEvent")
      .where($"stopSignEvent" isNotNull)
      .select($"stopSignEvent.*")
      .as[StopSignEvent]

    // Processing events

    // Matches each `path around event` to a sequence of vertices in the road network, and then finds the closest
    // vertex in the aforementioned sequence for the event position.
    val stopSignEventsMatchedOnPath: RDD[StopSignEventProjectionOnPath] =
      stopSignEvents.rdd.map(findClosestVertexOnPathAroundEvent(optimizedMap, cacheManager))

    // Clusters stop sign events based on their event positions.
    val stopSignEventClusters: RDD[Cluster[StopSignEventProjectionOnPath]] =
      findStopSignEventClusters(stopSignEventsMatchedOnPath)

    // At this point, we have a set of clusters. Each cluster contains a set of stop sign events which are supposed to
    // belong to the same `real` stop sign somewhere in the vicinity. In other words, each cluster corresponds to one
    // `real` stop sign. Given a set of clusters, we want to derive the positions of the `real` stop signs.
    //
    // To do so, we make two assumptions:
    //   - stop signs are located next to the roads. That is why the following logic uses projected points
    //     (i.e. points on the roads) of event positions instead of event positions themselves.
    //   - event positions are uniformly distributed around the `real` stop sign event. That is why the following logic
    //     averages fraction values of projected points.
    // It might happen that stop sign events of one cluster are projected onto different vertices. To simplify this
    // case, the code firstly groups stop sign events by vertices they are projected on, and then takes into account
    // only the biggest group to compute the average.
    val mostProbableStopSignPositions: RDD[VertexFraction] =
      stopSignEventClusters.map(findMostProbableStopSignPositionOnEventPath)

    // Preparing geoJson output

    val stopSignEventsMatchedOnPathGeoJson = stopSignEventsMatchedOnPath
      .map(toGeoJsonByPosition)

    val mostProbableStopSignPositionsGeoJson = mostProbableStopSignPositions
      .map(toGeoJsonByPosition(optimizedMap, cacheManager))

    // Publishing geoJson output

    publish(groupByOutputTileId(
              config,
              mostProbableStopSignPositionsGeoJson ++ stopSignEventsMatchedOnPathGeoJson),
            config)

    sparkSession.stop()

    logger.info("Application successfully finished")
  }
}

case class Config(sdiiCatalogHrn: HRN,
                  optimizedMapCatalogHrn: HRN,
                  optimizedMapCatalogVersion: Long,
                  outputCatalog: HRN) {
  val sdiiLayer = "sample-index-layer"
  val outputLayer = "stop-signs"
  val outputLayerLevel = HereTileLevel(14)
}

object Config {
  def apply(context: PipelineContext): Config =
    new Config(
      context.inputCatalogDescription("sdii-catalog").hrn,
      context.inputCatalogDescription("optimized-map-catalog").hrn,
      context.job
        .getOrElse(throw new Exception("No version for optimized-map-catalog"))
        .inputCatalogs("optimized-map-catalog")
        .version,
      context.config.outputCatalog
    )
}

object Steps {
  import org.apache.spark.sql.functions._

  case class StopSignEvent(eventPosition: GeoCoordinate, pathAroundEvent: Seq[GeoCoordinate])

  case class StopSignEventProjectionOnPath(event: StopSignEvent,
                                           vertexOnEventPath: Vertex,
                                           fractionOnVertex: Double,
                                           projectedPoint: GeoCoordinate)

  case class VertexFraction(vertex: Vertex, fraction: Double)

  implicit object StopSignEventProjectionOnPathGeoCoordinateOperations
      extends GeoCoordinateOperations[StopSignEventProjectionOnPath] {
    override def latitude(p: StopSignEventProjectionOnPath): Double =
      p.event.eventPosition.latitude

    override def longitude(p: StopSignEventProjectionOnPath): Double =
      p.event.eventPosition.longitude
  }

  val extractStopSignEventFromSdiiMessage: UserDefinedFunction = udf {
    (lat: Seq[Double], lon: Seq[Double], timestamp: Seq[Long], eventTimestamp: Long) =>
      val idxAfterEvent = timestamp.indexWhere(_ >= eventTimestamp)
      val tripTimestampOffset = 10.seconds.toMillis

      if (eventTimestamp < timestamp.head || idxAfterEvent == -1) {
        None
      } else {
        val positionAfterEvent = GeoCoordinate(lat(idxAfterEvent), lon(idxAfterEvent))

        val pathAround = (lat, lon, timestamp).zipped
          .dropWhile(_._3 < eventTimestamp - tripTimestampOffset)
          .takeWhile(_._3 <= eventTimestamp + tripTimestampOffset)
          .map {
            case (_lat, _lon, _) => GeoCoordinate(_lat, _lon)
          }
          .toSeq

        Some(StopSignEvent(positionAfterEvent, pathAround))
      }
  }

  def findClosestVertexOnPathAroundEvent(optimizedMap: Catalog, cacheManager: CacheManager)(
      event: StopSignEvent): StopSignEventProjectionOnPath = {
    val pathMatcher = PathMatchers.carPathMatcher[GeoCoordinate](optimizedMap, cacheManager)
    val geometries = PropertyMaps.geometry(optimizedMap, cacheManager)
    val verticesOnMatchedPath = pathMatcher
      .matchPath(event.pathAroundEvent)
      .results
      .collect {
        case OnRoad(projection) => projection.element
      }
      .distinct
    val (closestVertex, closestVertexProjection) = verticesOnMatchedPath
      .map(
        vertex =>
          vertex -> LineStrings
            .pointProjection(event.eventPosition, geometries(vertex), GeoProjections.sinusoidal))
      .minBy { case (_, projection) => projection.distanceInMeters }

    StopSignEventProjectionOnPath(event,
                                  closestVertex,
                                  closestVertexProjection.fraction,
                                  closestVertexProjection.nearest)
  }

  def findStopSignEventClusters(stopSignAndClosestVertex: RDD[StopSignEventProjectionOnPath])
      : RDD[Cluster[StopSignEventProjectionOnPath]] = {
    val neighborhoodRadiusInMeters: Double = 50.0
    val partitionBufferZoneInMeters: Double = 100.0
    val minNeighbors: Int = 1

    new DistributedClustering[StopSignEventProjectionOnPath](
      neighborhoodRadiusInMeters,
      minNeighbors,
      partitionBufferZoneInMeters).apply(stopSignAndClosestVertex)
  }

  def findMostProbableStopSignPositionOnEventPath(
      cluster: Cluster[StopSignEventProjectionOnPath]): VertexFraction = {
    val (mostPopularVertex, stopSignsOnVertex) = cluster.events
      .groupBy(projectedSign => projectedSign.vertexOnEventPath)
      .maxBy { case (_, entries) => entries.size }

    val fractions = stopSignsOnVertex.map(_.fractionOnVertex)
    val fractionAverage = fractions.sum / fractions.size

    VertexFraction(mostPopularVertex, fractionAverage)
  }

  def groupByOutputTileId(config: Config,
                          geoJsonByPosition: RDD[(GeoCoordinate, Feature[GeoCoordinate])])
      : RDD[(TileId, Iterable[Feature[GeoCoordinate]])] = {
    val outputResolver = new HereTileResolver(config.outputLayerLevel)

    geoJsonByPosition
      .groupBy { case (position, _) => outputResolver.fromCoordinate(position) }
      .mapValues(_.map { case (_, feature) => feature })
  }

  def toGeoJsonByPosition(optimizedMap: Catalog, cacheManager: CacheManager)(
      vf: VertexFraction): (GeoCoordinate, Feature[GeoCoordinate]) = {
    val geometries = PropertyMaps.geometry(optimizedMap, cacheManager)
    val geoCoordinate = LineStrings.pointForFraction(geometries(vf.vertex), vf.fraction)
    geoCoordinate -> geojson.Feature(geojson.Point(geoCoordinate), Some(JsObject.empty))
  }

  def toGeoJsonByPosition(
      p: StopSignEventProjectionOnPath): (GeoCoordinate, Feature[GeoCoordinate]) =
    p.event.eventPosition ->
      geojson.Feature(LineString(Seq(p.event.eventPosition, p.projectedPoint)),
                      Some(JsObject.empty))

  def publish(geoJsonByTile: RDD[(TileId, Iterable[Feature[GeoCoordinate]])],
              config: Config): Unit = {
    val masterActorSystem: ActorSystem = DataClientSparkContextUtils.context.actorSystem
    val masterPublishApi = DataClient(masterActorSystem).publishApi(config.outputCatalog)
    val latestVersion = masterPublishApi.getBaseVersion().awaitResult()

    // TODO: Properly handle dependencies parameter.
    val batchToken =
      masterPublishApi.startBatch2(latestVersion, Some(Seq(config.outputLayer))).awaitResult()
    geoJsonByTile
      .mapValues(points =>
        Json.toBytes(Json.toJson(geojson.FeatureCollection(points.to[immutable.Seq]))))
      .mapPartitions({ partitions =>
        val workerActorSystem = DataClientSparkContextUtils.context.actorSystem
        val workerPublishApi = DataClient(workerActorSystem).publishApi(config.outputCatalog)
        val workerWriteEngine = DataEngine(workerActorSystem).writeEngine(config.outputCatalog)
        val committedPartitions: Iterator[CommitPartition] = {
          partitions.map {
            case (tileId, bytes) =>
              val newPartition =
                NewPartition(
                  partition = tileId.value.toString,
                  layer = config.outputLayer,
                  data = NewPartition.ByteArrayData(bytes)
                )
              workerWriteEngine.put(newPartition).awaitResult()
          }
        }
        Seq(workerPublishApi.publishToBatch(batchToken, committedPartitions).awaitResult()).iterator
      })
      .collect()

    masterPublishApi.completeBatch(batchToken).awaitResult()
    ()
  }
}
