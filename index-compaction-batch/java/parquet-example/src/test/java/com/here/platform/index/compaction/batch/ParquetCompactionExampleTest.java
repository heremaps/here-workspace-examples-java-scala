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
package com.here.platform.index.compaction.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.here.platform.index.compaction.batch.util.CustomProtoWriteSupport;
import com.here.platform.index.compaction.batch.util.SdiiMessageParquetWriterBuilder;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.proto.ProtoParquetReader;
import org.junit.Test;

public class ParquetCompactionExampleTest {

  @Test
  public void testMerge() throws IOException {
    long receivedTime = System.currentTimeMillis();
    double longitude = 10d;
    double latitude = 10d;

    SdiiMessage.Message sdiiMessageOne = prepareSDIIMessage(receivedTime, longitude, latitude);
    SdiiMessage.Message sdiiMessageTwo =
        prepareSDIIMessage(receivedTime, longitude + 0.01d, latitude + 0.01d);
    SdiiMessage.Message sdiiMessageThree =
        prepareSDIIMessage(receivedTime, longitude + 0.01d, latitude - 0.01d);
    SdiiMessage.Message sdiiMessageFour =
        prepareSDIIMessage(receivedTime, longitude - 0.01d, latitude - 0.01d);

    byte[] messageOne = sdiiMessageOne.toByteArray();
    byte[] messageTwo = sdiiMessageTwo.toByteArray();
    byte[] messageThree = sdiiMessageThree.toByteArray();
    byte[] messageFour = sdiiMessageFour.toByteArray();

    List<byte[]> messagesGroupOne = Arrays.asList(messageOne, messageTwo);
    Iterator<byte[]> messagesGroupOneIterator = messagesGroupOne.iterator();

    List<byte[]> messagesGroupTwo = Arrays.asList(messageThree, messageFour);
    Iterator<byte[]> messagesGroupTwoIterator = messagesGroupTwo.iterator();

    byte[] aggregatedMessagesGroupOne = aggregate(messagesGroupOneIterator);
    byte[] aggregatedMessagesGroupTwo = aggregate(messagesGroupTwoIterator);

    Iterator<byte[]> aggregatedMessagesIterator =
        Arrays.asList(aggregatedMessagesGroupOne, aggregatedMessagesGroupTwo).iterator();

    ParquetCompactionExample parquetCompactionExample = new ParquetCompactionExample();
    byte[] mergedMessages =
        parquetCompactionExample.merge(new HashMap<>(), aggregatedMessagesIterator);

    Path temporaryDirectory = Files.createTempDirectory("parquetTmp");
    temporaryDirectory.toFile().deleteOnExit();
    Path parquetFilePath = temporaryDirectory.resolve(UUID.randomUUID().toString());
    Files.write(parquetFilePath, mergedMessages);
    ParquetReader<SdiiMessage.Message.Builder> reader =
        ProtoParquetReader.<SdiiMessage.Message.Builder>builder(
                new org.apache.hadoop.fs.Path(parquetFilePath.toString()))
            .build();

    assertEquals(sdiiMessageOne, reader.read().build());
    assertEquals(sdiiMessageTwo, reader.read().build());
    assertEquals(sdiiMessageThree, reader.read().build());
    assertEquals(sdiiMessageFour, reader.read().build());
    assertNull(reader.read());
    reader.close();

    parquetFilePath.toFile().delete();
  }

  private SdiiMessage.Message prepareSDIIMessage(
      long receivedTime, double longitude, double latitude) {
    SdiiCommon.Envelope envelope =
        SdiiCommon.Envelope.newBuilder().setVersion("version1").setSubmitter("submitter1").build();
    SdiiCommon.Path path =
        SdiiCommon.Path.newBuilder()
            .addPositionEstimate(
                SdiiCommon.PositionEstimate.newBuilder()
                    .setTimeStampUTCMs(receivedTime)
                    .setPositionType(SdiiCommon.PositionEstimate.PositionTypeEnum.FILTERED)
                    .setLongitudeDeg(longitude)
                    .setLatitudeDeg(latitude)
                    .setHorizontalAccuracyM(2d)
                    .build())
            .build();
    SdiiCommon.PathEvents pathEvents =
        SdiiCommon.PathEvents.newBuilder()
            .addSignRecognition(
                SdiiCommon.SignRecognition.newBuilder()
                    .setRoadSignPermanency(
                        SdiiCommon.SignRecognition.RoadSignPermanencyEnum.VARIABLE)
                    .setRoadSignType(SdiiCommon.SignRecognition.RoadSignTypeEnum.SPEED_LIMIT_START)
                    .setTimeStampUTCMs(receivedTime)
                    .build())
            .build();
    return SdiiMessage.Message.newBuilder()
        .setEnvelope(envelope)
        .setPath(path)
        .setPathEvents(pathEvents)
        .build();
  }

  private byte[] aggregate(Iterator<byte[]> messages) throws IOException {
    Path temporaryDirectory = Files.createTempDirectory("parquet");
    temporaryDirectory.toFile().deleteOnExit();
    Path parquetFilePath = temporaryDirectory.resolve(UUID.randomUUID().toString());
    try {
      ParquetWriter<SdiiMessage.Message> parquetWriter = createParquetWriter(parquetFilePath);
      while (messages.hasNext()) {
        parquetWriter.write(SdiiMessage.Message.parseFrom(messages.next()));
      }
      parquetWriter.close();
      return Files.readAllBytes(parquetFilePath);
    } finally {
      deleteTempParquetFile(temporaryDirectory, parquetFilePath);
    }
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

  private void deleteTempParquetFile(Path temporaryDirectory, Path parquetFilePath) {
    if (parquetFilePath != null) {
      if (parquetFilePath.toFile().exists()) {
        parquetFilePath.toFile().delete();
      }
      Path crcPath =
          temporaryDirectory.resolve("." + parquetFilePath.getFileName().toString() + ".crc");
      if (crcPath.toFile().exists()) {
        crcPath.toFile().delete();
      }
    }
  }
}
