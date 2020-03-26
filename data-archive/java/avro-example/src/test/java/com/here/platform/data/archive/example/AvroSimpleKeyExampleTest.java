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

package com.here.platform.data.archive.example;

import static org.junit.Assert.assertEquals;

import com.here.olp.util.quad.factory.HereQuadFactory;
import com.here.platform.dal.custom.MetadataName;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class AvroSimpleKeyExampleTest {

  private AvroSimpleKeyExample example = new AvroSimpleKeyExample();

  @Test
  public void testGetKeys() throws IOException {
    long timestamp =
        ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli();
    double longitude = 10d;
    double latitude = 10d;
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    cal.setTimeInMillis(timestamp);

    SdiiMessage.Message sdiiMessage = prepareSDIIMessage(timestamp, true, longitude, latitude);
    Map<MetadataName, String> metadata = new HashMap<>();
    metadata.put(MetadataName.INGESTION_TIME, String.valueOf(timestamp));
    Map<String, Object> keys = example.getKeys(metadata, sdiiMessage.toByteArray());
    assertEquals(
        HereQuadFactory.INSTANCE
            .getMapQuadByLocation(latitude, longitude, AvroSimpleKeyExample.ZOOM_LEVEL)
            .getLongKey(),
        keys.get(AvroSimpleKeyExample.TILE_ID));
    assertEquals(timestamp, keys.get(AvroSimpleKeyExample.INGESTION_TIME));
    assertEquals(
        SdiiCommon.SignRecognition.getDescriptor().getName(),
        keys.get(AvroSimpleKeyExample.EVENT_TYPE));
  }

  @Test
  public void testAggregate() throws IOException {
    SdiiMessage.Message message1 = prepareSDIIMessage(System.currentTimeMillis(), true, 10d, 10d);
    SdiiMessage.Message message2 = prepareSDIIMessage(System.currentTimeMillis(), true, 10d, 10d);
    SdiiMessage.Message message3 = prepareSDIIMessage(System.currentTimeMillis(), true, 10d, 10d);
    List<byte[]> messagesList =
        Arrays.asList(message1.toByteArray(), message2.toByteArray(), message3.toByteArray());

    File tmpFile = File.createTempFile("test", ".avro");
    tmpFile.deleteOnExit();
    FileUtils.writeByteArrayToFile(
        tmpFile, example.aggregate(new HashMap<>(), messagesList.iterator()));

    List<SdiiMessage.Message> list = AvroHelper.fromFile(tmpFile, SdiiMessage.Message.class);
    assertEquals(3, list.size());
    assertSDIIMessagesAreEqual(message1, list.get(0));
    assertSDIIMessagesAreEqual(message2, list.get(1));
    assertSDIIMessagesAreEqual(message3, list.get(2));
  }

  @Test
  public void testMerge() throws IOException {
    SdiiMessage.Message message1 = prepareSDIIMessage(System.currentTimeMillis(), true, 10d, 10d);
    SdiiMessage.Message message2 = prepareSDIIMessage(System.currentTimeMillis(), true, 10d, 10d);
    SdiiMessage.Message message3 = prepareSDIIMessage(System.currentTimeMillis(), true, 10d, 10d);
    SdiiMessage.Message message4 = prepareSDIIMessage(System.currentTimeMillis(), true, 10d, 10d);
    byte[] archiveBytesMessage1 =
        example.aggregate(
            null, Arrays.asList(message1.toByteArray(), message2.toByteArray()).iterator());
    byte[] archiveBytesMessage2 =
        example.aggregate(
            null, Arrays.asList(message3.toByteArray(), message4.toByteArray()).iterator());
    File tmpFile = File.createTempFile("test", ".avro");
    tmpFile.deleteOnExit();

    FileUtils.writeByteArrayToFile(
        tmpFile,
        example.merge(
            new HashMap<>(), Arrays.asList(archiveBytesMessage1, archiveBytesMessage2).iterator()));
    List<SdiiMessage.Message> list = AvroHelper.fromFile(tmpFile, SdiiMessage.Message.class);
    assertEquals(4, list.size());
    assertSDIIMessagesAreEqual(message1, list.get(0));
    assertSDIIMessagesAreEqual(message2, list.get(1));
    assertSDIIMessagesAreEqual(message3, list.get(2));
    assertSDIIMessagesAreEqual(message4, list.get(3));
  }

  private SdiiMessage.Message prepareSDIIMessage(
      long receivedTime, boolean withPathEvent, double longitude, double latitude)
      throws IOException {
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
    SdiiMessage.Message.Builder sdipMessageBuilder =
        SdiiMessage.Message.newBuilder().setEnvelope(envelope).setPath(path);

    if (withPathEvent) {
      return sdipMessageBuilder.setPathEvents(pathEvents).build();
    } else {
      return sdipMessageBuilder.build();
    }
  }

  // Only check the fields we are interested in (on deserialize, default values are also included,
  // don't want to check those)
  private void assertSDIIMessagesAreEqual(
      SdiiMessage.Message expected, SdiiMessage.Message actual) {
    SdiiCommon.Envelope expectedEnvelope = expected.getEnvelope();
    SdiiCommon.Envelope actualEnvelope = actual.getEnvelope();
    assertEquals(expectedEnvelope.getVersion(), actualEnvelope.getVersion());
    assertEquals(expectedEnvelope.getSubmitter(), actualEnvelope.getSubmitter());

    SdiiCommon.PositionEstimate expectedPositionEstimate =
        expected.getPath().getPositionEstimate(0);
    SdiiCommon.PositionEstimate actualPositionEstimate = actual.getPath().getPositionEstimate(0);
    assertEquals(
        expectedPositionEstimate.getTimeStampUTCMs(), actualPositionEstimate.getTimeStampUTCMs());
    assertEquals(
        expectedPositionEstimate.getPositionType(), actualPositionEstimate.getPositionType());
    assertEquals(
        expectedPositionEstimate.getLongitudeDeg(), actualPositionEstimate.getLongitudeDeg(), 0);
    assertEquals(
        expectedPositionEstimate.getLatitudeDeg(), actualPositionEstimate.getLatitudeDeg(), 0);
    assertEquals(
        expectedPositionEstimate.getHorizontalAccuracyM(),
        actualPositionEstimate.getHorizontalAccuracyM(),
        0);

    SdiiCommon.SignRecognition expectedSignRecognition =
        expected.getPathEvents().getSignRecognition(0);
    SdiiCommon.SignRecognition actualSignRecognition = actual.getPathEvents().getSignRecognition(0);
    assertEquals(
        expectedSignRecognition.getTimeStampUTCMs(), actualSignRecognition.getTimeStampUTCMs());
    assertEquals(
        expectedSignRecognition.getRoadSignType(), actualSignRecognition.getRoadSignType());
    assertEquals(
        expectedSignRecognition.getRoadSignPermanency(),
        actualSignRecognition.getRoadSignPermanency());
  }
}
