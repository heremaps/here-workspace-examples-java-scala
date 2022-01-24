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

import static com.here.platform.data.archive.example.SensorisParquetSplittedUDFExample.TIMEWINDOW_DURATION;
import static com.here.platform.data.archive.example.TestFixtures.prepareSensorisMessage;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.dal.custom.MetadataName;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.sensoris.messages.data.DataMessage;
import org.sensoris.messages.data.DataMessages;

public class SensorisParquetSplittedUDFExampleTest {

  SensorisParquetSplittedUDFExample example = new SensorisParquetSplittedUDFExample();

  long now = System.currentTimeMillis();
  double longitude = 10d; // if zoom level is 8, tileId = 1442800
  double latitude = 10d;

  DataMessage m1 =
      prepareSensorisMessage(
          Collections.singletonList(now),
          Collections.singletonList(new GeoCoordinate(latitude, longitude)));
  DataMessage m2 =
      prepareSensorisMessage(
          Collections.singletonList(now),
          Collections.singletonList(new GeoCoordinate(latitude + 0.01d, longitude + 0.01d)));
  DataMessage m3 =
      prepareSensorisMessage(
          Collections.singletonList(now),
          Collections.singletonList(new GeoCoordinate(latitude - 0.01d, longitude + 0.01d)));
  DataMessage m4 =
      prepareSensorisMessage(
          Collections.singletonList(now),
          Collections.singletonList(new GeoCoordinate(latitude + 0.01d, longitude - 0.01d)));

  byte[] b1 = DataMessages.newBuilder().addDataMessage(m1).build().toByteArray();
  byte[] b2 = DataMessages.newBuilder().addDataMessage(m2).build().toByteArray();
  byte[] b3 = DataMessages.newBuilder().addDataMessage(m3).build().toByteArray();
  byte[] b4 = DataMessages.newBuilder().addDataMessage(m4).build().toByteArray();

  @Test
  public void testGetKeys() throws InvalidProtocolBufferException {
    DataMessage m1 =
        prepareSensorisMessage(
            Arrays.asList(now, now + TIMEWINDOW_DURATION),
            Arrays.asList(
                new GeoCoordinate(53.38217, 14.04649), // tileId = 92259 at level 8
                new GeoCoordinate(53.38722, 14.05036))); // tileId = 92259 at level 8
    DataMessage m2 =
        prepareSensorisMessage(
            Arrays.asList(
                now,
                example.truncateToTimeWindow(now + TIMEWINDOW_DURATION),
                now + TIMEWINDOW_DURATION),
            Arrays.asList(
                new GeoCoordinate(53.38853, 14.05455), // tileId = 92259 at level 8
                new GeoCoordinate(53.38965, 14.05777), // tileId = 92259 at level 8
                new GeoCoordinate(53.39363, 14.07643))); // tileId = 92262 at level 8

    Long nowWindow = example.truncateToTimeWindow(now);
    Long laterWindow = example.truncateToTimeWindow(now + TIMEWINDOW_DURATION);
    Assert.assertNotEquals(nowWindow, laterWindow);
    Assert.assertEquals(TIMEWINDOW_DURATION, laterWindow - nowWindow);

    DataMessages messages = DataMessages.newBuilder().addDataMessage(m1).addDataMessage(m2).build();
    Map<MetadataName, String> metadata = new HashMap<>();
    metadata.put(MetadataName.INGESTION_TIME, String.valueOf(now));
    Map<Map<String, Object>, byte[]> indexKeysAndPayloads =
        example.getSplittedKeys(metadata, messages.toByteArray());

    Assert.assertThat(indexKeysAndPayloads.size(), is(3));

    Map<String, Object> nowAndWestKey = new HashMap<>();
    nowAndWestKey.put(SensorisParquetSplittedUDFExample.TILE_ID, 92259L);
    nowAndWestKey.put(SensorisParquetSplittedUDFExample.EVENT_TIME, nowWindow);
    Map<String, Object> laterAndWestKey = new HashMap<>();
    laterAndWestKey.put(SensorisParquetSplittedUDFExample.TILE_ID, 92259L);
    laterAndWestKey.put(SensorisParquetSplittedUDFExample.EVENT_TIME, laterWindow);
    Map<String, Object> laterAndEastKey = new HashMap<>();
    laterAndEastKey.put(SensorisParquetSplittedUDFExample.TILE_ID, 92262L);
    laterAndEastKey.put(SensorisParquetSplittedUDFExample.EVENT_TIME, laterWindow);

    Assert.assertTrue(
        indexKeysAndPayloads
            .keySet()
            .containsAll(Arrays.asList(nowAndWestKey, laterAndWestKey, laterAndEastKey)));

    DataMessages messagesInEarlierWindowAndWesternTile =
        DataMessages.parseFrom(indexKeysAndPayloads.get(nowAndWestKey));
    Assert.assertThat(messagesInEarlierWindowAndWesternTile.getDataMessageCount(), is(2));
    Assert.assertEquals(messagesInEarlierWindowAndWesternTile, messages);

    DataMessages messagesInLaterWindowAndWesternTile =
        DataMessages.parseFrom(indexKeysAndPayloads.get(laterAndWestKey));
    Assert.assertThat(messagesInLaterWindowAndWesternTile.getDataMessageCount(), is(2));
    Assert.assertEquals(messagesInLaterWindowAndWesternTile, messages);

    DataMessages messagesInLaterWindowAndEasternTile =
        DataMessages.parseFrom(indexKeysAndPayloads.get(laterAndEastKey));
    Assert.assertThat(messagesInLaterWindowAndEasternTile.getDataMessageCount(), is(1));
    Assert.assertEquals(
        messagesInLaterWindowAndEasternTile.getDataMessageList(), Collections.singletonList(m2));
  }

  @Test
  public void testAggregate() {
    List<byte[]> list = Arrays.asList(b1, b2);
    Iterator<byte[]> it = list.iterator();

    byte[] aggregatedByteArray = example.aggregate(new HashMap<>(), it);

    List<DataMessage> aggregatedMessageList = ExampleReader.readMessages(aggregatedByteArray);

    assertEquals(m1, aggregatedMessageList.get(0));
    assertEquals(m2, aggregatedMessageList.get(1));
  }
}
