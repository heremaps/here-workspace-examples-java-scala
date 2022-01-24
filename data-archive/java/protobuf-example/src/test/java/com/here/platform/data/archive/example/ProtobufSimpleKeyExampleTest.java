/*
 * Copyright (C) 2017-2022 HERE Europe B.V.
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

import static org.junit.Assert.assertEquals;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.olp.util.quad.factory.HereQuadFactory;
import com.here.platform.dal.custom.MetadataName;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import com.here.sdii.v3.SdiiMessageList;
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
import org.junit.Assert;
import org.junit.Test;

public class ProtobufSimpleKeyExampleTest {

  ProtobufSimpleKeyExample example = new ProtobufSimpleKeyExample();

  long receivedTime = System.currentTimeMillis();
  double longitude = 10d;
  double latitude = 10d;

  SdiiMessage.Message m1 = prepareSDIIMessage(receivedTime, true, longitude, latitude);
  SdiiMessage.Message m2 =
      prepareSDIIMessage(receivedTime, true, longitude + 0.01d, latitude + 0.01d);
  SdiiMessage.Message m3 =
      prepareSDIIMessage(receivedTime, true, longitude + 0.01d, latitude - 0.01d);
  SdiiMessage.Message m4 =
      prepareSDIIMessage(receivedTime, true, longitude - 0.01d, latitude - 0.01d);

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

    SdiiMessage.Message sdiiMessage = prepareSDIIMessage(timestamp, true, longitude, latitude);
    Map<MetadataName, String> metadata = new HashMap<>();
    metadata.put(MetadataName.INGESTION_TIME, String.valueOf(timestamp));
    Map<String, Object> keys = example.getKeys(metadata, sdiiMessage.toByteArray());
    Assert.assertEquals(
        HereQuadFactory.INSTANCE
            .getMapQuadByLocation(latitude, longitude, ProtobufSimpleKeyExample.ZOOM_LEVEL)
            .getLongKey(),
        keys.get(ProtobufSimpleKeyExample.TILE_ID));
    Assert.assertEquals(timestamp, keys.get(ProtobufSimpleKeyExample.INGESTION_TIME));
    Assert.assertEquals(
        SdiiCommon.SignRecognition.getDescriptor().getName(),
        keys.get(ProtobufSimpleKeyExample.EVENT_TYPE));
  }

  @Test
  public void testAggregate() throws InvalidProtocolBufferException {
    List<byte[]> list = Arrays.asList(b1, b2);
    Iterator it = list.iterator();

    byte[] aggregatedByteArray = example.aggregate(new HashMap<>(), it);

    List<SdiiMessage.Message> aggregatedMessageList =
        SdiiMessageList.MessageList.parseFrom(aggregatedByteArray).getMessageList();

    assertEquals(m1, aggregatedMessageList.get(0));
    assertEquals(m2, aggregatedMessageList.get(1));
  }

  private SdiiMessage.Message prepareSDIIMessage(
      long receivedTime, boolean withPathEvent, double longitude, double latitude) {
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
}
