/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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

import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.here.olp.util.quad.factory.HereQuadFactory;
import com.here.platform.dal.custom.MetadataName;
import com.here.platform.dal.custom.MultiKeysUDF;
import com.here.sdii.v3.SdiiCommon;
import com.here.sdii.v3.SdiiMessage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.StreamSupport;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.protobuf.ProtobufData;
import org.apache.avro.protobuf.ProtobufDatumReader;
import org.apache.avro.protobuf.ProtobufDatumWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvroMultiKeysExample implements MultiKeysUDF {

  private static final Logger LOG = LoggerFactory.getLogger(AvroMultiKeysExample.class);
  private static final String PROBE = "probe";
  private static final String TILE_ID = "tileId";
  private static final String INGESTION_TIME = "ingestionTime";
  private static final String EVENT_TYPE = "eventType";
  private static final int ZOOM_LEVEL = 8;

  @Override
  public Map<String, List<Object>> getMultipleKeys(
      Map<MetadataName, String> metadata, byte[] payload) {
    SdiiMessage.Message sdiiMessage;
    try {
      sdiiMessage = SdiiMessage.Message.parseFrom(payload);
    } catch (InvalidProtocolBufferException e) {
      LOG.error("Parsing SDII Message error", e);
      return null;
    }

    Map<String, List<Object>> result = new HashMap<>();

    // Extract tileId
    if (sdiiMessage.getPath() != null
        && sdiiMessage.getPath().getPositionEstimateList() != null
        && !sdiiMessage.getPath().getPositionEstimateList().isEmpty()) {
      Set<Object> tileIds = new HashSet<>();
      for (SdiiCommon.PositionEstimate positionEstimate :
          sdiiMessage.getPath().getPositionEstimateList()) {
        tileIds.add(
            HereQuadFactory.INSTANCE
                .getMapQuadByLocation(
                    positionEstimate.getLatitudeDeg(),
                    positionEstimate.getLongitudeDeg(),
                    ZOOM_LEVEL)
                .getLongKey());
      }
      result.put(TILE_ID, new ArrayList<>(tileIds));
    } else {
      // Tile Id based on Beijing coordinates used as a place holder if position estimate or
      // timestamp is missing
      result.put(TILE_ID, Collections.singletonList(95140));
    }

    // Extract ingestionTime
    result.put(
        INGESTION_TIME,
        Collections.singletonList(Long.parseLong(metadata.get(MetadataName.INGESTION_TIME))));

    // Extract eventType
    Set<Object> eventTypes = new HashSet<>();
    if (!sdiiMessage.hasPathEvents()) {
      eventTypes.add(PROBE);
    } else {
      SdiiCommon.PathEvents pathEvents = sdiiMessage.getPathEvents();
      for (Descriptors.FieldDescriptor fieldDescriptor : pathEvents.getAllFields().keySet()) {
        eventTypes.add(fieldDescriptor.getName());
      }
    }
    if (eventTypes.isEmpty()) {
      eventTypes.add("unknown");
    }
    result.put(EVENT_TYPE, new ArrayList<>(eventTypes));

    return result;
  }

  @Override
  public byte[] aggregate(Map<String, Object> keys, Iterator<byte[]> messages) {
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
      return aggregateProtobufMessagesAsAvro(sdiiMessages, SdiiMessage.Message.class);
    } catch (Exception e) {
      LOG.error("Aggregation errors....", e);
    }
    return null;
  }

  public static <V> byte[] aggregateProtobufMessagesAsAvro(Iterator<V> iterator, Class<V> cls)
      throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    asAvro(iterator, cls, os);
    os.close();
    return os.toByteArray();
  }

  public static <V> void asAvro(Iterator<V> iterator, Class<V> cls, OutputStream os)
      throws Exception {
    ProtobufDatumWriter<V> writer = new ProtobufDatumWriter<>(cls);
    try (DataFileWriter<V> dataFileWriter = new DataFileWriter<>(writer)) {
      Schema schema = ProtobufData.get().getSchema(cls);
      dataFileWriter.create(schema, os);
      while (iterator.hasNext()) {
        dataFileWriter.append(iterator.next());
      }
    }
  }

  public static <V> List<V> fromFile(File file, Class<V> cls) throws IOException {
    ProtobufDatumReader<V> reader = new ProtobufDatumReader<>(cls);
    DataFileReader<V> dataFileReader = new DataFileReader<>(file, reader);
    List<V> list = new LinkedList<>();
    while (dataFileReader.hasNext()) {
      list.add(dataFileReader.next());
    }
    dataFileReader.close();
    return list;
  }
}
