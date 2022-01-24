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

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.dal.custom.MetadataName;
import com.here.platform.dal.custom.SplittedUDF;
import com.here.platform.data.archive.example.parquet.ParquetHelper;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.integration.herecommons.geospatial.HereTileLevel;
import com.here.platform.location.integration.herecommons.geospatial.javadsl.HereTileResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.sensoris.messages.data.DataMessage;
import org.sensoris.messages.data.DataMessages;
import org.sensoris.types.spatial.PositionAndAccuracy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SensorisParquetSplittedUDFExample implements SplittedUDF {

  private static final Logger LOG =
      LoggerFactory.getLogger(SensorisParquetSplittedUDFExample.class);

  public static final double SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION = Math.pow(10, 8);
  public static final double SENSORIS_GEOGRAPHIC_ALTITUDE_RESOLUTION = Math.pow(10, 3);

  // Values must match index layer configuration. They can also be looked up via the Config Service.
  static final String TILE_ID = "tileId";
  static final String EVENT_TIME = "eventTime";

  static final int ZOOM_LEVEL = 8;
  static final long TIMEWINDOW_DURATION = TimeUnit.MINUTES.toMillis(10);

  private final HereTileResolver HERE_TILE_RESOLVER =
      new HereTileResolver(new HereTileLevel(ZOOM_LEVEL));

  @Override
  public Map<Map<String, Object>, byte[]> getSplittedKeys(
      Map<MetadataName, String> map, byte[] bytes) {
    try {
      DataMessages messages = DataMessages.parseFrom(bytes);
      Map<Map<String, Object>, List<DataMessage>> keyedMessages = new HashMap<>();
      for (DataMessage message : messages.getDataMessageList()) {
        if (message.getEventGroupCount() > 0) {
          Set<Keys> indexValues =
              message
                  .getEventGroupList()
                  .stream()
                  .flatMap(
                      eventGroup ->
                          eventGroup
                              .getLocalizationCategory()
                              .getVehiclePositionAndOrientationList()
                              .stream())
                  .map(
                      positionAndOrientation -> {
                        long timestamp =
                            positionAndOrientation
                                .getEnvelope()
                                .getTimestamp()
                                .getPosixTime()
                                .getValue();
                        long truncatedTime = truncateToTimeWindow(timestamp);

                        PositionAndAccuracy.Geographic geolocation =
                            positionAndOrientation.getPositionAndAccuracy().getGeographicWgs84();
                        GeoCoordinate geoCoordinate =
                            new GeoCoordinate(
                                geolocation.getLatitude().getValue()
                                    / SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION,
                                geolocation.getLongitude().getValue()
                                    / SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION);
                        long tileId = HERE_TILE_RESOLVER.fromCoordinate(geoCoordinate);

                        return new Keys(truncatedTime, tileId);
                      })
                  .collect(Collectors.toSet());

          for (Keys keyValues : indexValues) {
            Map<String, Object> indexKey = new HashMap<>();
            indexKey.put(EVENT_TIME, keyValues.timewindow);
            indexKey.put(TILE_ID, keyValues.tileId);
            keyedMessages.computeIfAbsent(indexKey, keyMap -> new ArrayList<>()).add(message);
          }
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Index keys: {}", keyedMessages.keySet());
      }

      return keyedMessages
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
    return ParquetHelper.aggregateMessages(
        messages,
        bytes -> {
          try {
            return DataMessages.parseFrom(bytes).getDataMessageList();
          } catch (InvalidProtocolBufferException e) {
            LOG.error("Error parsing Sensoris DataMessages", e);
            return Collections.emptyList();
          }
        });
  }

  public long truncateToTimeWindow(long timestamp) {
    return timestamp - (timestamp % TIMEWINDOW_DURATION);
  }

  private class Keys {

    private final long timewindow;
    private final long tileId;

    Keys(long timewindow, long tileId) {
      this.timewindow = timewindow;
      this.tileId = tileId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Keys keys = (Keys) o;
      return timewindow == keys.timewindow && tileId == keys.tileId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(timewindow, tileId);
    }

    @Override
    public String toString() {
      return "{" + "timewindow=" + timewindow + ", tileId=" + tileId + '}';
    }
  }
}
