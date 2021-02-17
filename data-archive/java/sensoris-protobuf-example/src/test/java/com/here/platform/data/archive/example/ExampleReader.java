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

package com.here.platform.data.archive.example;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.hrn.HRN;
import com.here.platform.data.client.flink.javadsl.FlinkDataClient;
import com.here.platform.data.client.flink.javadsl.FlinkQueryApi;
import com.here.platform.data.client.flink.javadsl.FlinkReadEngine;
import com.here.platform.data.client.javadsl.IndexPartition;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Iterator;
import java.util.Objects;
import org.sensoris.categories.localization.LocalizationCategory;
import org.sensoris.messages.data.DataMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example reader for testing. To use Spark to process data stored in the index layer, the
 * Data Client Library provides the IndexDataFrameReader.
 *
 * @see <a
 *     href="https://developer.here.com/documentation/data-client-library/dev_guide/client/index-layer-spark-support.html">IndexDataFrameReader</a>
 */
public class ExampleReader {
  private static final Logger LOG = LoggerFactory.getLogger(ExampleReader.class);

  private static final String TILE_ID = "tileId";
  private static final String EVENT_TIME = "eventTime";

  public static void main(String[] args) {
    Config config = ConfigFactory.load();
    HRN catalogHrn = HRN.fromString(config.getString("sink.hrn"));
    String layer = config.getString("sink.layer");

    FlinkDataClient flinkDataClient = null;

    try {
      flinkDataClient = new FlinkDataClient();
      FlinkQueryApi flinkQueryApi = flinkDataClient.queryApi(catalogHrn);
      FlinkReadEngine flinkReadEngine = flinkDataClient.readEngine(catalogHrn);

      // Build RSQL query string
      StringBuilder query = new StringBuilder();
      if (config.hasPath("example-reader.query.tileId")) {
        query
            .append(TILE_ID)
            .append("==")
            .append(config.getLong("example-reader.query.tile-id"))
            .append(";");
      }
      if (config.hasPath("example-reader.query.start-timewindow")) {
        query
            .append(EVENT_TIME)
            .append(">=")
            .append(config.getLong("example-reader.query.start-timewindow"))
            .append(";");
      }
      if (config.hasPath("example-reader.query.end-timewindow")) {
        query
            .append(EVENT_TIME)
            .append("<")
            .append(config.getLong("example-reader.query.end-timewindow"))
            .append(";");
      }
      if (query.length() == 0) {
        query.append("size=ge=0");
      } else {
        query.deleteCharAt(query.length() - 1);
      }

      LOG.info("Looking up data from {}/{} with query {}", catalogHrn.toString(), layer, query);
      Iterator<IndexPartition> indexQueryResult =
          flinkQueryApi.queryIndexAsIterator(layer, query.toString());
      indexQueryResult.forEachRemaining(
          indexPartition -> {
            byte[] partitionData = flinkReadEngine.getDataAsBytes(indexPartition);
            try {
              DataMessages dataMessages = DataMessages.parseFrom(partitionData);
              logResult(indexPartition, dataMessages);
            } catch (InvalidProtocolBufferException e) {
              LOG.error(
                  "Error parsing Sensoris DataMessages for dataHandle: {}",
                  indexPartition.getDataHandle(),
                  e);
            }
          });
    } finally {
      LOG.debug("Shutting down...");

      if (Objects.nonNull(flinkDataClient)) {
        flinkDataClient.terminate();
      }
    }
  }

  private static void logResult(IndexPartition partition, DataMessages result) {
    LOG.info("For partition {}, there were {} messages", partition, result.getDataMessageCount());
    LocalizationCategory localizationCategory =
        result.getDataMessage(0).getEventGroup(0).getLocalizationCategory();
    LOG.info(
        "First message has {} geopoints, starting at {}.",
        localizationCategory.getVehiclePositionAndOrientationCount(),
        localizationCategory
            .getVehiclePositionAndOrientation(0)
            .getPositionAndAccuracy()
            .getGeographicWgs84());
  }
}
