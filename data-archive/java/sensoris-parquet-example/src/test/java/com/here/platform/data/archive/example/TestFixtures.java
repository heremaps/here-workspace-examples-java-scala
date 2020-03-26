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

import static com.here.platform.data.archive.example.SensorisParquetSplittedUDFExample.SENSORIS_GEOGRAPHIC_ALTITUDE_RESOLUTION;
import static com.here.platform.data.archive.example.SensorisParquetSplittedUDFExample.SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION;

import com.google.protobuf.Int64Value;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import java.util.List;
import org.sensoris.categories.localization.LocalizationCategory;
import org.sensoris.categories.localization.VehiclePositionAndOrientation;
import org.sensoris.categories.trafficmaneuver.Maneuver;
import org.sensoris.categories.trafficmaneuver.TrafficManeuverCategory;
import org.sensoris.categories.trafficregulation.TrafficRegulationCategory;
import org.sensoris.categories.trafficregulation.TrafficSign;
import org.sensoris.messages.data.DataMessage;
import org.sensoris.messages.data.EventGroup;
import org.sensoris.types.base.EventEnvelope;
import org.sensoris.types.base.Timestamp;
import org.sensoris.types.spatial.PositionAndAccuracy;

public final class TestFixtures {

  public static DataMessage prepareSensorisMessage(
      List<Long> timestamps, List<GeoCoordinate> geoCoordinates) {
    LocalizationCategory.Builder localizationCategoryBuilder = LocalizationCategory.newBuilder();

    for (int i = 0; i < timestamps.size(); i++) {
      GeoCoordinate geoCoordinate = geoCoordinates.get(i);
      localizationCategoryBuilder.addVehiclePositionAndOrientation(
          VehiclePositionAndOrientation.newBuilder()
              .setEnvelope(
                  EventEnvelope.newBuilder()
                      .setTimestamp(
                          Timestamp.newBuilder()
                              .setPosixTime(Int64Value.newBuilder().setValue(timestamps.get(i)))))
              .setPositionAndAccuracy(
                  PositionAndAccuracy.newBuilder()
                      .setGeographicWgs84(
                          prepareGeographic(
                              geoCoordinate.getLatitude(), geoCoordinate.getLongitude(), 0))));
    }

    EventGroup.Builder eventGroupBuilder =
        EventGroup.newBuilder().setLocalizationCategory(localizationCategoryBuilder);

    eventGroupBuilder.setTrafficRegulationCategory(
        TrafficRegulationCategory.newBuilder()
            .addTrafficSign(
                TrafficSign.newBuilder()
                    .setTypeAndConfidence(
                        TrafficSign.TypeAndConfidence.newBuilder()
                            .setType(TrafficSign.TypeAndConfidence.Type.SPEED_LIMIT))
                    .setPermanencyAndConfidence(
                        TrafficSign.PermanencyAndConfidence.newBuilder()
                            .setType(TrafficSign.PermanencyAndConfidence.Type.VARIABLE))));

    eventGroupBuilder.setTrafficManeuverCategory(
        TrafficManeuverCategory.newBuilder()
            .addManeuver(
                Maneuver.newBuilder()
                    .setTypeAndConfidence(
                        Maneuver.TypeAndConfidence.newBuilder()
                            .setType(Maneuver.TypeAndConfidence.Type.BREAKING))));

    DataMessage.Builder messageBuilder = DataMessage.newBuilder().addEventGroup(eventGroupBuilder);

    return messageBuilder.build();
  }

  /** Convert from decimal degrees to Int64Value */
  private static PositionAndAccuracy.Geographic prepareGeographic(
      double latitude, double longitude, double altitude) {
    return PositionAndAccuracy.Geographic.newBuilder()
        .setLatitude(prepareInt64Value(latitude, SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION))
        .setLongitude(prepareInt64Value(longitude, SENSORIS_GEOGRAPHIC_SURFACE_RESOLUTION))
        .setAltitude(prepareInt64Value(altitude, SENSORIS_GEOGRAPHIC_ALTITUDE_RESOLUTION))
        .build();
  }

  private static org.sensoris.types.base.Int64Value prepareInt64Value(
      double degreeDecimal, double resolution) {
    return org.sensoris.types.base.Int64Value.newBuilder()
        .setValue(Double.valueOf(degreeDecimal * resolution).longValue())
        .build();
  }

  private TestFixtures() {}
}
