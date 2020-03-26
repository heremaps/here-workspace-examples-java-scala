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

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.olp.util.quad.factory.HereQuadFactory;
import com.here.platform.dal.custom.MetadataName;
import com.here.platform.dal.custom.SimpleUDF;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import com.here.sdii.v3.SdiiMessageList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtobufSimpleKeyExample implements SimpleUDF {

  private static final Logger LOG = LoggerFactory.getLogger(ProtobufSimpleKeyExample.class);
  private static final String PROBE = "probe";
  static final String TILE_ID = "tileId";
  static final String INGESTION_TIME = "ingestionTime";
  static final String EVENT_TYPE = "eventType";
  static final int ZOOM_LEVEL = 8;

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
    List<SdiiMessage.Message> aggregatedList = new ArrayList<>();
    while (messages.hasNext()) {
      byte[] data = messages.next();

      try {
        SdiiMessage.Message m = SdiiMessage.Message.parseFrom(data);
        aggregatedList.add(m);
      } catch (InvalidProtocolBufferException e) {
        LOG.error("Aggregation errors....", e);
        return null;
      }
    }

    return SdiiMessageList.MessageList.newBuilder()
        .addAllMessage(aggregatedList)
        .build()
        .toByteArray();
  }

  @Override
  public byte[] merge(Map<String, Object> keys, Iterator<byte[]> files) {
    List<SdiiMessage.Message> mergedList = new ArrayList<>();

    while (files.hasNext()) {
      byte[] data = files.next();

      try {
        SdiiMessageList.MessageList messageListProtoBuf =
            SdiiMessageList.MessageList.parseFrom(data);
        List<SdiiMessage.Message> list = messageListProtoBuf.getMessageList();
        mergedList.addAll(list);
      } catch (InvalidProtocolBufferException e) {
        LOG.error("Merge errors....", e);
        return null;
      }
    }

    return SdiiMessageList.MessageList.newBuilder().addAllMessage(mergedList).build().toByteArray();
  }
}
