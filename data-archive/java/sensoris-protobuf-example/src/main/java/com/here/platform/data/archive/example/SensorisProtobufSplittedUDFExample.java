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

package com.here.platform.data.archive.example;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.dal.custom.MetadataName;
import com.here.platform.dal.custom.SplittedUDF;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.integration.herecommons.geospatial.HereTileLevel;
import com.here.platform.location.integration.herecommons.geospatial.javadsl.HereTileResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sensoris.categories.localization.VehiclePositionAndOrientation;
import org.sensoris.messages.data.DataMessage;
import org.sensoris.messages.data.DataMessages;
import org.sensoris.types.spatial.PositionAndAccuracy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SensorisProtobufSplittedUDFExample implements SplittedUDF {

  private static final Logger LOG =
      LoggerFactory.getLogger(SensorisProtobufSplittedUDFExample.class);

  public static final double SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION = Math.pow(10, 8);
  public static final double SENSORIS_GEOGRAPHIC_ALTITUDE_RESOLUTION = Math.pow(10, 3);

  // Values must match index layer configuration. They can also be looked up via the Config Service.
  static final String TILE_ID = "tileId";
  static final String EVENT_TIME = "eventTime";
  static final int ZOOM_LEVEL = 8;

  private final HereTileResolver HERE_TILE_RESOLVER =
      new HereTileResolver(new HereTileLevel(ZOOM_LEVEL));

  @Override
  public Map<Map<String, Object>, byte[]> getSplittedKeys(
      Map<MetadataName, String> map, byte[] bytes) {
    try {
      DataMessages messages = DataMessages.parseFrom(bytes);

      LOG.debug("Processing {} DataMessage objects", messages.getDataMessageCount());

      // Extract index attributes for each message and track the list of messages matching each key
      Map<Map<String, Object>, List<DataMessage>> indexedMessages =
          messages
              .getDataMessageList()
              .stream()
              .filter(message -> message.getEventGroupCount() > 0)
              .collect(
                  Collectors.toMap(
                      message -> { // extract index attribute values
                        Map<String, Object> indexAttributes = new HashMap<>();
                        VehiclePositionAndOrientation firstPositionReport =
                            message
                                .getEventGroup(0)
                                .getLocalizationCategory()
                                .getVehiclePositionAndOrientation(0);
                        indexAttributes.put(
                            EVENT_TIME,
                            firstPositionReport
                                .getEnvelope()
                                .getTimestamp()
                                .getPosixTime()
                                .getValue());
                        PositionAndAccuracy.Geographic firstReportedGeolocation =
                            firstPositionReport.getPositionAndAccuracy().getGeographicWgs84();
                        indexAttributes.put(
                            TILE_ID,
                            HERE_TILE_RESOLVER.fromCoordinate(
                                new GeoCoordinate(
                                    firstReportedGeolocation.getLatitude().getValue()
                                        / SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION,
                                    firstReportedGeolocation.getLongitude().getValue()
                                        / SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION)));
                        return indexAttributes;
                      },
                      message -> { // hold the message in a list
                        List<DataMessage> wrapped = new ArrayList<>();
                        wrapped.add(message);
                        return wrapped;
                      },
                      (left, right) -> { // combine message lists with the same index tuple key
                        left.addAll(right);
                        return left;
                      }));

      if (LOG.isDebugEnabled()) {
        indexedMessages.forEach(
            (key, value) ->
                LOG.debug(
                    "{} messages keyed to {} from the same DataMessages container",
                    value.size(),
                    key));
      }

      // Convert list of DataMessage objects into DataMessages protobuf wrapper and serialize to
      // bytes
      return indexedMessages
          .entrySet()
          .stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  entry ->
                      DataMessages.newBuilder()
                          .addAllDataMessage(entry.getValue())
                          .build()
                          .toByteArray()));
    } catch (InvalidProtocolBufferException e) {
      LOG.error("Parsing Sensoris DataMessages error", e);
    }
    return null;
  }

  @Override
  public byte[] aggregate(Map<String, Object> keys, Iterator<byte[]> messages) {
    DataMessages.Builder combiningBuilder = DataMessages.newBuilder();

    while (messages.hasNext()) {
      byte[] data = messages.next();

      try {
        DataMessages m = DataMessages.parseFrom(data);
        combiningBuilder.addAllDataMessage(m.getDataMessageList());
      } catch (InvalidProtocolBufferException e) {
        LOG.error("Error trying to combine DataMessages", e);
      }
    }

    return combiningBuilder.build().toByteArray();
  }
}
