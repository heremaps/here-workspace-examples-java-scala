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
package com.here.platform.index.compaction.batch;

import com.here.platform.index.compaction.batch.util.CustomProtoWriteSupport;
import com.here.platform.index.compaction.batch.util.SdiiMessageParquetWriterBuilder;
import com.here.platform.index.compaction.core.CompactionUDF;
import com.here.sdii.v3.SdiiMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.proto.ProtoParquetReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParquetCompactionExample implements CompactionUDF {

  private static final long serialVersionUID = -6582452093309090015L;
  private static final Logger LOG = LoggerFactory.getLogger(ParquetCompactionExample.class);
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
  public byte[] merge(Map<String, Object> keys, Iterator<byte[]> files) {
    Path parquetTmpFilePath = null;
    Path parquetTargetFilePath = null;
    try {
      // Prepare input & output paths
      parquetTargetFilePath = tmpDir.resolve(UUID.randomUUID().toString());
      // Initialize parquet reader & writer
      ParquetReader<SdiiMessage.Message.Builder> parquetReader;
      ParquetWriter<SdiiMessage.Message> parquetWriter = createParquetWriter(parquetTargetFilePath);
      // Prepare input files, read all files and merge data
      while (files.hasNext()) {
        parquetTmpFilePath = tmpDir.resolve(UUID.randomUUID().toString());
        Files.write(parquetTmpFilePath, files.next());
        parquetReader =
            ProtoParquetReader.<SdiiMessage.Message.Builder>builder(
                    new org.apache.hadoop.fs.Path(parquetTmpFilePath.toString()))
                .build();
        for (SdiiMessage.Message.Builder sdiiMessageBuilder;
            (sdiiMessageBuilder = parquetReader.read()) != null; ) {
          parquetWriter.write(sdiiMessageBuilder.build());
        }
        parquetReader.close();
        deleteTempParquetFile(parquetTmpFilePath);
      }
      parquetWriter.close();
      return Files.readAllBytes(parquetTargetFilePath);
    } catch (IOException e) {
      LOG.error("Error merging files (key={})", keys.values(), e);
    } finally {
      deleteTempParquetFile(parquetTmpFilePath);
      deleteTempParquetFile(parquetTargetFilePath);
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
