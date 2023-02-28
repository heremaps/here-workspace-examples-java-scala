/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.platform.data.archive.example;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.olp.util.quad.factory.HereQuadFactory;
import com.here.platform.dal.custom.MetadataName;
import com.here.platform.dal.custom.SimpleUDF;
import com.here.platform.data.archive.example.util.CustomProtoWriteSupport;
import com.here.platform.data.archive.example.util.SdiiMessageParquetWriterBuilder;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParquetSimpleKeyExample implements SimpleUDF {

  private static final Logger LOG = LoggerFactory.getLogger(ParquetSimpleKeyExample.class);
  private static final String PROBE = "probe";
  static final String TILE_ID = "tileId";
  static final String INGESTION_TIME = "ingestionTime";
  static final String EVENT_TYPE = "eventType";
  static final int ZOOM_LEVEL = 8;
  private static Path tmpDir;

  static {
    try {
      tmpDir = Files.createTempDirectory("parquet");
      tmpDir.toFile().deleteOnExit();
    } catch (IOException e) {
      LOG.error("Error creating temp directory", e);
    }
  }

  @Override
  public Map<String, Object> getKeys(Map<MetadataName, String> metadata, byte[] payload) {
    SdiiMessage.Message sdiiMessage;
    try {
      sdiiMessage = SdiiMessage.Message.parseFrom(payload);
    } catch (InvalidProtocolBufferException e) {
      LOG.error("Parsing SDII Message error", e);
      return null;
    }

    Map<String, Object> result = new HashMap<>();

    // Extract tileId
    if (sdiiMessage.getPath() != null
        && sdiiMessage.getPath().getPositionEstimateList() != null
        && !sdiiMessage.getPath().getPositionEstimateList().isEmpty()) {
      TreeSet<SdiiCommon.PositionEstimate> positionEstimates =
          new TreeSet<>(Comparator.comparingLong(SdiiCommon.PositionEstimate::getTimeStampUTCMs));
      positionEstimates.addAll(sdiiMessage.getPath().getPositionEstimateList());
      result.put(
          TILE_ID,
          HereQuadFactory.INSTANCE
              .getMapQuadByLocation(
                  positionEstimates.first().getLatitudeDeg(),
                  positionEstimates.first().getLongitudeDeg(),
                  ZOOM_LEVEL)
              .getLongKey());
    } else {
      result.put(TILE_ID, null);
    }

    // Extract ingestionTime
    result.put(INGESTION_TIME, Long.parseLong(metadata.get(MetadataName.INGESTION_TIME)));

    // Extract eventType
    if (!sdiiMessage.hasPathEvents()) {
      result.put(EVENT_TYPE, PROBE);
    } else {
      String event = "unknown";
      List<SdiiCommon.SignRecognition> signRecognitionList =
          sdiiMessage.getPathEvents().getSignRecognitionList();
      if (!signRecognitionList.isEmpty()) {
        event = SdiiCommon.SignRecognition.getDescriptor().getName();
      }
      result.put(EVENT_TYPE, event);
    }

    return result;
  }

  @Override
  public byte[] aggregate(Map<String, Object> keys, Iterator<byte[]> messages) {
    Path parquetFilePath = null;
    try {
      parquetFilePath = tmpDir.resolve(UUID.randomUUID().toString());

      ParquetWriter<SdiiMessage.Message> parquetWriter = createParquetWriter(parquetFilePath);

      while (messages.hasNext()) {
        parquetWriter.write(SdiiMessage.Message.parseFrom(messages.next()));
      }

      parquetWriter.close();
      return Files.readAllBytes(parquetFilePath);
    } catch (IOException e) {
      LOG.error("Errors creating parquet temp file....", e);
    } finally {
      deleteTempParquetFile(parquetFilePath);
    }

    return null;
  }

  private ParquetWriter<SdiiMessage.Message> createParquetWriter(Path parquetTargetFilePath)
      throws IOException {
    Configuration conf = new Configuration();
    CustomProtoWriteSupport.setWriteSpecsCompliant(conf, true);
    return new SdiiMessageParquetWriterBuilder(
            new org.apache.hadoop.fs.Path(parquetTargetFilePath.toString()))
        .withConf(conf)
        .build();
  }

  private void deleteTempParquetFile(Path parquetPath) {
    if (parquetPath != null) {
      boolean deleteStatus;
      if (parquetPath.toFile().exists()) {
        deleteStatus = parquetPath.toFile().delete();
        LOG.debug("Delete operation (file: {}, status: {})", parquetPath.toString(), deleteStatus);
      }
      Path crcPath = tmpDir.resolve("." + parquetPath.getFileName().toString() + ".crc");
      if (crcPath.toFile().exists()) {
        deleteStatus = crcPath.toFile().delete();
        LOG.debug("Delete operation (crc file: {}, status: {})", crcPath.toString(), deleteStatus);
      }
    }
  }
}
