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

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import com.here.sdii.v3.SdiiMessageList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

public class ProtobufCompactionExampleTest {

  @Test
  public void testMerge() throws InvalidProtocolBufferException {
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

    ProtobufCompactionExample protobufCompactionExample = new ProtobufCompactionExample();
    byte[] mergedMessages =
        protobufCompactionExample.merge(new HashMap<>(), aggregatedMessagesIterator);

    List<SdiiMessage.Message> mergedSdiiMessages =
        SdiiMessageList.MessageList.parseFrom(mergedMessages).getMessageList();

    assertEquals(sdiiMessageOne, mergedSdiiMessages.get(0));
    assertEquals(sdiiMessageTwo, mergedSdiiMessages.get(1));
    assertEquals(sdiiMessageThree, mergedSdiiMessages.get(2));
    assertEquals(sdiiMessageFour, mergedSdiiMessages.get(3));
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

  public byte[] aggregate(Iterator<byte[]> messages) throws InvalidProtocolBufferException {
    List<SdiiMessage.Message> aggregatedMessages = new ArrayList<>();
    while (messages.hasNext()) {
      byte[] message = messages.next();
      SdiiMessage.Message sdiiMessage = SdiiMessage.Message.parseFrom(message);
      aggregatedMessages.add(sdiiMessage);
    }
    return SdiiMessageList.MessageList.newBuilder()
        .addAllMessage(aggregatedMessages)
        .build()
        .toByteArray();
  }
}
