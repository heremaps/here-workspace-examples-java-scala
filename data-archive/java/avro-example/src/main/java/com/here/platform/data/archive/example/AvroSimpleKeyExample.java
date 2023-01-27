/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.olp.util.quad.factory.HereQuadFactory;
import com.here.platform.dal.custom.MetadataName;
import com.here.platform.dal.custom.SimpleUDF;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.stream.StreamSupport;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvroSimpleKeyExample implements SimpleUDF {

  private static final Logger LOG = LoggerFactory.getLogger(AvroSimpleKeyExample.class);
  private static final String PROBE = "probe";
  static final String TILE_ID = "tileId";
  static final String INGESTION_TIME = "ingestionTime";
  static final String EVENT_TYPE = "eventType";
  static final int ZOOM_LEVEL = 8;
  private Counter getKeysDuration;
  private Counter aggregateDuration;

  @Override
  public void open(Configuration parameters, RuntimeContext runtimeContext) {
    getKeysDuration = runtimeContext.getMetricGroup().counter("getKeysDuration");
    aggregateDuration = runtimeContext.getMetricGroup().counter("aggregateDuration");
  }

  @Override
  public Map<String, Object> getKeys(Map<MetadataName, String> metadata, byte[] payload) {
    long startTime = System.currentTimeMillis();
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

    getKeysDuration.inc(System.currentTimeMillis() - startTime);
    return result;
  }

  @Override
  public byte[] aggregate(Map<String, Object> keys, Iterator<byte[]> messages) {
    long startTime = System.currentTimeMillis();
    try {
      Iterator<SdiiMessage.Message> sdiiMessages =
          StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(messages, Spliterator.SIZED), true)
              .map(
                  item -> {
                    try {
                      return SdiiMessage.Message.parseFrom(item);
                    } catch (InvalidProtocolBufferException e) {
                      throw new IllegalStateException(e);
                    }
                  })
              .iterator();
      return AvroHelper.aggregateProtobufMessagesAsAvro(sdiiMessages, SdiiMessage.Message.class);
    } catch (Exception e) {
      LOG.error("Aggregation errors....", e);
    } finally {
      aggregateDuration.inc(System.currentTimeMillis() - startTime);
    }
    return null;
  }
}
