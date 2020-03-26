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

import static com.here.platform.data.archive.example.TestFixtures.prepareSensorisMessage;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.dal.custom.MetadataName;
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

public class SensorisProtobufSplittedUDFExampleTest {
  SensorisProtobufSplittedUDFExample example = new SensorisProtobufSplittedUDFExample();

  long now = System.currentTimeMillis();
  double longitude = 10d; // if zoom level is 8, tileId = 90175
  double latitude = 10d;

  DataMessage m1 = prepareSensorisMessage(now, latitude, longitude);
  DataMessage m2 = prepareSensorisMessage(now, latitude + 0.01d, longitude + 0.01d);
  DataMessage m3 = prepareSensorisMessage(now, latitude - 0.01d, longitude + 0.01d);
  DataMessage m4 = prepareSensorisMessage(now, latitude + 0.01d, longitude - 0.01d);

  byte[] b1 = wrapInDataMessages(Collections.singletonList(m1));
  byte[] b2 = wrapInDataMessages(Collections.singletonList(m2));
  byte[] b3 = wrapInDataMessages(Collections.singletonList(m3));
  byte[] b4 = wrapInDataMessages(Collections.singletonList(m4));

  @Test
  public void testGetKeys() throws InvalidProtocolBufferException {
    DataMessage m1 = prepareSensorisMessage(now, 53.38217, 14.04649); // tileId = 92259 at level 8
    DataMessage m2 = prepareSensorisMessage(now, 53.39363, 14.07643); // tileId = 92262 at level 8

    DataMessages messages = DataMessages.newBuilder().addDataMessage(m1).addDataMessage(m2).build();
    Map<MetadataName, String> metadata = new HashMap<>();
    metadata.put(MetadataName.INGESTION_TIME, String.valueOf(now));
    Map<Map<String, Object>, byte[]> indexKeysAndPayloads =
        example.getSplittedKeys(metadata, messages.toByteArray());

    Assert.assertThat(indexKeysAndPayloads.size(), is(2));

    Map<String, Object> nowAndWesternTile = new HashMap<>();
    nowAndWesternTile.put(SensorisProtobufSplittedUDFExample.TILE_ID, 92259L);
    nowAndWesternTile.put(SensorisProtobufSplittedUDFExample.EVENT_TIME, now);
    Map<String, Object> nowAndEasternTile = new HashMap<>();
    nowAndEasternTile.put(SensorisProtobufSplittedUDFExample.TILE_ID, 92262L);
    nowAndEasternTile.put(SensorisProtobufSplittedUDFExample.EVENT_TIME, now);

    Assert.assertTrue(
        indexKeysAndPayloads
            .keySet()
            .containsAll(Arrays.asList(nowAndWesternTile, nowAndEasternTile)));
    Assert.assertEquals(
        m1, DataMessages.parseFrom(indexKeysAndPayloads.get(nowAndWesternTile)).getDataMessage(0));
    Assert.assertEquals(
        m2, DataMessages.parseFrom(indexKeysAndPayloads.get(nowAndEasternTile)).getDataMessage(0));
  }

  @Test
  public void testAggregate() throws InvalidProtocolBufferException {
    List<byte[]> list = Arrays.asList(b1, b2);
    Iterator<byte[]> it = list.iterator();

    byte[] aggregatedByteArray = example.aggregate(new HashMap<>(), it);

    List<DataMessage> aggregatedMessageList =
        DataMessages.parseFrom(aggregatedByteArray).getDataMessageList();

    assertEquals(m1, aggregatedMessageList.get(0));
    assertEquals(m2, aggregatedMessageList.get(1));
  }

  @Test
  public void testMerge() throws InvalidProtocolBufferException {
    List<byte[]> list = Arrays.asList(b1, b2);
    Iterator<byte[]> it = list.iterator();

    List<byte[]> list2 = Arrays.asList(b3, b4);
    Iterator<byte[]> it2 = list2.iterator();

    byte[] a1 = example.aggregate(new HashMap<>(), it);
    byte[] a2 = example.aggregate(new HashMap<>(), it2);

    Iterator<byte[]> mergedIt = Arrays.asList(a1, a2).iterator();

    byte[] mergedByteArray = example.merge(new HashMap<>(), mergedIt);

    List<DataMessage> mergedMessageList =
        DataMessages.parseFrom(mergedByteArray).getDataMessageList();

    assertEquals(m1, mergedMessageList.get(0));
    assertEquals(m2, mergedMessageList.get(1));
    assertEquals(m3, mergedMessageList.get(2));
    assertEquals(m4, mergedMessageList.get(3));
  }

  private byte[] wrapInDataMessages(List<DataMessage> messages) {
    return DataMessages.newBuilder().addAllDataMessage(messages).build().toByteArray();
  }
}
