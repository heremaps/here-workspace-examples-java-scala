/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

package com.here.platform.data.archive.example.parquet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.proto.ProtoParquetReader;
import org.sensoris.messages.data.DataMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParquetHelper {

  private static final Logger LOG = LoggerFactory.getLogger(ParquetHelper.class);
  private static Path tmpDir;

  static {
    try {
      tmpDir = Files.createTempDirectory("parquet");
      tmpDir.toFile().deleteOnExit();
    } catch (IOException e) {
      LOG.error("Error creating temp directory", e);
    }
  }

  /**
   * Aggregate given message payloads into parquet format
   *
   * @param messages payloads for messages with the same index attribute key values
   * @param parser transforms each payload to a List of Sensoris DataMessage
   * @return
   */
  public static byte[] aggregateMessages(
      Iterator<byte[]> messages, Function<byte[], List<DataMessage>> parser) {
    Path parquetFilePath = null;
    try {
      parquetFilePath = tmpDir.resolve(UUID.randomUUID().toString());

      ParquetWriter<DataMessage> parquetWriter = createParquetWriter(parquetFilePath);

      while (messages.hasNext()) {
        List<DataMessage> payloadMessages = parser.apply(messages.next());
        for (DataMessage message : payloadMessages) {
          parquetWriter.write(message);
        }
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

  public static byte[] mergeFiles(Iterator<byte[]> files) {
    Path parquetTargetFilePath = null;
    Path parquetTmpFilePath = null;
    try {
      // Prepare input & output paths
      parquetTargetFilePath = tmpDir.resolve(UUID.randomUUID().toString());

      // Initialize parquet reader & writer
      ParquetReader<DataMessage.Builder> parquetReader;
      ParquetWriter<DataMessage> parquetWriter = createParquetWriter(parquetTargetFilePath);

      // Prepare input files, read all files and merge data
      while (files.hasNext()) {
        parquetTmpFilePath = tmpDir.resolve(UUID.randomUUID().toString());
        Files.write(parquetTmpFilePath, files.next());

        parquetReader =
            ProtoParquetReader.<DataMessage.Builder>builder(
                    new org.apache.hadoop.fs.Path(parquetTmpFilePath.toString()))
                .build();
        for (DataMessage.Builder sensorisMessageBuilder;
            (sensorisMessageBuilder = parquetReader.read()) != null; ) {
          parquetWriter.write(sensorisMessageBuilder.build());
        }
        parquetReader.close();
        deleteTempParquetFile(parquetTmpFilePath);
      }
      parquetWriter.close();

      return Files.readAllBytes(parquetTargetFilePath);
    } catch (IOException e) {
      LOG.error("Errors merging files....", e);
    } finally {
      deleteTempParquetFile(parquetTmpFilePath);
      deleteTempParquetFile(parquetTargetFilePath);
    }

    return null;
  }

  private static ParquetWriter<DataMessage> createParquetWriter(Path parquetFilePath)
      throws IOException {
    Configuration conf = new Configuration();
    CustomProtoWriteSupport.setWriteSpecsCompliant(conf, true);
    return new SensorisMessageParquetWriterBuilder(
            new org.apache.hadoop.fs.Path(parquetFilePath.toString()))
        .withConf(conf)
        .build();
  }

  private static void deleteTempParquetFile(Path parquetPath) {
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
