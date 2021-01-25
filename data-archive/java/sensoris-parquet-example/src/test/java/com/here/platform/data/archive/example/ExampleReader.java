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

import com.here.hrn.HRN;
import com.here.platform.data.client.flink.javadsl.FlinkDataClient;
import com.here.platform.data.client.flink.javadsl.FlinkQueryApi;
import com.here.platform.data.client.flink.javadsl.FlinkReadEngine;
import com.here.platform.data.client.javadsl.IndexPartition;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.proto.ProtoParquetReader;
import org.sensoris.categories.localization.LocalizationCategory;
import org.sensoris.messages.data.DataMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example reader for testing. To use Spark to process data stored in the index layer, the
 * Data Client Library provides the IndexDataFrameReader.
 *
 * @see <a
 *     href="https://developer.here.com/olp/documentation/data-client-library/dev_guide/client/index-layer-spark-support.html">IndexDataFrameReader</a>
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
            List<DataMessage> dataMessages = readMessages(partitionData);
            logResult(indexPartition, dataMessages);
          });
    } finally {
      LOG.debug("Shutting down...");

      if (Objects.nonNull(flinkDataClient)) {
        flinkDataClient.terminate();
      }
    }
  }

  public static List<DataMessage> readMessages(byte[] partitionData) {
    List<DataMessage> partitionMessages = new ArrayList<>();

    ParquetReader<DataMessage.Builder> reader;
    DataMessage.Builder item;
    Path parquetTmpFilePath = null;
    try {
      Path tmpDir = Files.createTempDirectory("parquetTmp");
      tmpDir.toFile().deleteOnExit();

      parquetTmpFilePath = tmpDir.resolve(UUID.randomUUID().toString());
      Files.write(parquetTmpFilePath, partitionData);
      reader =
          ProtoParquetReader.<DataMessage.Builder>builder(
                  new org.apache.hadoop.fs.Path(parquetTmpFilePath.toString()))
              .build();
      while (true) {
        item = reader.read();
        if (item == null) {
          break;
        } else {
          partitionMessages.add(item.build());
        }
      }
      reader.close();
      parquetTmpFilePath.toFile().delete();
    } catch (IOException e) {
      LOG.error("Errors creating parquet temp file....", e);
    } finally {
      if (parquetTmpFilePath != null) {
        parquetTmpFilePath.toFile().delete();
      }
    }

    return partitionMessages;
  }

  private static void logResult(IndexPartition partition, List<DataMessage> result) {
    LOG.info("For partition {}, there were {} messages", partition, result.size());
    LOG.info("Partition size: {}", partition.getDataSize());
    LocalizationCategory localizationCategory =
        result.get(0).getEventGroup(0).getLocalizationCategory();
    LOG.info(
        "First message has {} geopoints, starting at {}.",
        localizationCategory.getVehiclePositionAndOrientationCount(),
        localizationCategory
            .getVehiclePositionAndOrientation(0)
            .getPositionAndAccuracy()
            .getGeographicWgs84());
  }
}
