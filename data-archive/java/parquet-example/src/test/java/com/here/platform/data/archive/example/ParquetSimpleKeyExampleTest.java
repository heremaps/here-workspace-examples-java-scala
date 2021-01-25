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

import static org.junit.Assert.assertEquals;

import com.here.olp.util.quad.factory.HereQuadFactory;
import com.here.platform.dal.custom.MetadataName;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.proto.ProtoParquetReader;
import org.junit.Assert;
import org.junit.Test;

public class ParquetSimpleKeyExampleTest {

  ParquetSimpleKeyExample example = new ParquetSimpleKeyExample();

  long receivedTime = System.currentTimeMillis();
  double longitude = 10d;
  double latitude = 10d;

  SdiiMessage.Message m1 = prepareSDIIMessage(receivedTime, true, longitude, latitude, "version1");
  SdiiMessage.Message m2 =
      prepareSDIIMessage(receivedTime, true, longitude + 0.01d, latitude + 0.01d, "version2");
  SdiiMessage.Message m3 =
      prepareSDIIMessage(receivedTime, true, longitude + 0.01d, latitude - 0.01d, "version3");
  SdiiMessage.Message m4 =
      prepareSDIIMessage(receivedTime, true, longitude - 0.01d, latitude - 0.01d, "version4");

  byte[] b1 = m1.toByteArray();
  byte[] b2 = m2.toByteArray();
  byte[] b3 = m3.toByteArray();
  byte[] b4 = m4.toByteArray();

  @Test
  public void testGetKeys() {
    long timestamp =
        ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli();
    double longitude = 10d;
    double latitude = 10d;
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    cal.setTimeInMillis(timestamp);

    SdiiMessage.Message sdiiMessage =
        prepareSDIIMessage(timestamp, true, longitude, latitude, "version1");
    Map<MetadataName, String> metadata = new HashMap<>();
    metadata.put(MetadataName.INGESTION_TIME, String.valueOf(timestamp));
    Map<String, Object> keys = example.getKeys(metadata, sdiiMessage.toByteArray());
    Assert.assertEquals(
        HereQuadFactory.INSTANCE
            .getMapQuadByLocation(latitude, longitude, ParquetSimpleKeyExample.ZOOM_LEVEL)
            .getLongKey(),
        keys.get(ParquetSimpleKeyExample.TILE_ID));
    Assert.assertEquals(timestamp, keys.get(ParquetSimpleKeyExample.INGESTION_TIME));
    Assert.assertEquals(
        SdiiCommon.SignRecognition.getDescriptor().getName(),
        keys.get(ParquetSimpleKeyExample.EVENT_TYPE));
  }

  @Test
  public void testAggregate() throws IOException {
    List<byte[]> list = Arrays.asList(b1, b2);
    Iterator it = list.iterator();
    byte[] aggregatedByteArray = example.aggregate(new HashMap<>(), it);
    Path tmpDir = Files.createTempDirectory("parquetTmp");
    tmpDir.toFile().deleteOnExit();
    Path parquetTmpFilePath = tmpDir.resolve(UUID.randomUUID().toString());
    Files.write(parquetTmpFilePath, aggregatedByteArray);
    ParquetReader<SdiiMessage.Message.Builder> reader =
        ProtoParquetReader.<SdiiMessage.Message.Builder>builder(
                new org.apache.hadoop.fs.Path(parquetTmpFilePath.toString()))
            .build();

    assertEquals(m1, reader.read().build());
    assertEquals(m2, reader.read().build());
    assertEquals(null, reader.read());
    reader.close();
    parquetTmpFilePath.toFile().delete();
  }

  @Test
  public void testMerge() throws IOException {
    List<byte[]> list = Arrays.asList(b1, b2);
    Iterator it = list.iterator();

    List<byte[]> list2 = Arrays.asList(b3, b4);
    Iterator it2 = list2.iterator();

    byte[] a1 = example.aggregate(new HashMap<>(), it);
    byte[] a2 = example.aggregate(new HashMap<>(), it2);

    Iterator mergedIt = Arrays.asList(a1, a2).iterator();

    byte[] mergedByteArray = example.merge(new HashMap<>(), mergedIt);
    Path tmpDir = Files.createTempDirectory("parquetTmp");
    tmpDir.toFile().deleteOnExit();
    Path parquetTmpFilePath = tmpDir.resolve(UUID.randomUUID().toString());
    Files.write(parquetTmpFilePath, mergedByteArray);
    ParquetReader<SdiiMessage.Message.Builder> reader =
        ProtoParquetReader.<SdiiMessage.Message.Builder>builder(
                new org.apache.hadoop.fs.Path(parquetTmpFilePath.toString()))
            .build();

    assertEquals(m1, reader.read().build());
    assertEquals(m2, reader.read().build());
    assertEquals(m3, reader.read().build());
    assertEquals(m4, reader.read().build());
    assertEquals(null, reader.read());
    reader.close();

    parquetTmpFilePath.toFile().delete();
  }

  private SdiiMessage.Message prepareSDIIMessage(
      long receivedTime, boolean withPathEvent, double longitude, double latitude, String version) {
    SdiiCommon.Envelope envelope =
        SdiiCommon.Envelope.newBuilder().setVersion(version).setSubmitter("submitter1").build();
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
    SdiiMessage.Message.Builder sdipMessageBuilder =
        SdiiMessage.Message.newBuilder().setEnvelope(envelope).setPath(path);

    if (withPathEvent) {
      return sdipMessageBuilder.setPathEvents(pathEvents).build();
    } else {
      return sdipMessageBuilder.build();
    }
  }
}
