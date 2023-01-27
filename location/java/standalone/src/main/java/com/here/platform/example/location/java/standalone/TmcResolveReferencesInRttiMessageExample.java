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

package com.here.platform.example.location.java.standalone;

import static com.here.traffic.realtime.v2.TmcReference.TmcDirection.*;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.referencing.BidirectionalLinearLocation;
import com.here.platform.location.referencing.LinearLocation;
import com.here.platform.location.referencing.LocationReferenceResolver;
import com.here.platform.location.referencing.javadsl.LocationReferenceResolvers;
import com.here.platform.location.tpeg2.etl.ExtendedTMCLocationReference;
import com.here.platform.location.tpeg2.etl.TMCLocationReference;
import com.here.traffic.realtime.v2.LocationTypes;
import com.here.traffic.realtime.v2.SupplementaryLocationReferenceOuterClass;
import com.here.traffic.realtime.v2.TmcReference;
import com.here.traffic.realtime.v2.Traffic;
import com.here.traffic.realtime.v2.Traffic.TrafficItems;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TmcResolveReferencesInRttiMessageExample {
  public static void main(final String[] args) throws Exception {
    final BaseClient baseClient = BaseClientJava.instance();

    try {
      final OptimizedMapLayers optimizedMap =
          OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
              .usingBaseClient(baseClient)
              .newInstance()
              .version(1293L);

      final LocationReferenceResolver<ExtendedTMCLocationReference, BidirectionalLinearLocation>
          resolver = new LocationReferenceResolvers(optimizedMap).extendedTmc();

      final TrafficItems rttiMessage =
          TrafficItems.parseFrom(
              TmcResolveReferencesInRttiMessageExample.class
                  .getClassLoader()
                  .getResourceAsStream("rtti-message.bin"));

      final List<LinearLocation> resolvedLocations =
          rttiMessage
              .getItemsList()
              .stream()
              .flatMap(TmcResolveReferencesInRttiMessageExample::convertTmcReferences)
              .map(tpeg2TmcRef -> resolver.resolve(tpeg2TmcRef).getLocation())
              .collect(Collectors.toList());

      outputResolvedLocations(resolvedLocations);
    } finally {
      baseClient.shutdown();
    }
  }

  private static Stream<ExtendedTMCLocationReference> convertTmcReferences(
      final Traffic.TrafficItem rttiItem) {
    final SupplementaryLocationReferenceOuterClass.SupplementaryLocationReference
        supplementaryLocationRef = rttiItem.getSupplementaryLocationRef();
    if (supplementaryLocationRef == null) {
      return Stream.empty();
    }

    final TmcReference.CountryTableCode supplementaryCountryCode =
        supplementaryLocationRef.getCountryTableCode();

    return supplementaryLocationRef
        .getLocationReferenceList()
        .stream()
        .filter(locationRef -> locationRef.getLocationTypeKey() == LocationTypes.LocationType.TMC)
        .map(SupplementaryLocationReferenceOuterClass.LocationRef::getTmcRef)
        .flatMap(tmcRef -> tmcRef.getTmcsList().stream())
        .filter(
            rttiTmc ->
                supplementaryCountryCode.isInitialized()
                    || rttiTmc.getCountryTableCode().isInitialized())
        .map(
            rttiTmc -> {
              final TmcReference.CountryTableCode tmcCountryTable = rttiTmc.getCountryTableCode();
              final TmcReference.CountryTableCode countryTable =
                  tmcCountryTable.isInitialized() ? supplementaryCountryCode : tmcCountryTable;

              return convertTmcReference(countryTable, rttiTmc);
            });
  }

  private static ExtendedTMCLocationReference convertTmcReference(
      final TmcReference.CountryTableCode countryTable, final TmcReference.Tmc tmc) {
    final TmcReference.TmcDirection roadwayDirection = tmc.getRoadwayDirection();

    final boolean direction =
        roadwayDirection == AT_NEGATIVE || roadwayDirection == APPROACHING_NEGATIVE;
    final int extent =
        roadwayDirection == APPROACHING_NEGATIVE || roadwayDirection == APPROACHING_POSITIVE
            ? 1
            : 0;
    final boolean usePrimaryInternal =
        roadwayDirection == AT_NEGATIVE || roadwayDirection == AT_POSITIVE;

    return new ExtendedTMCLocationReference(
        "1.1",
        Optional.of(
            new TMCLocationReference(
                Integer.parseInt(tmc.getCode()),
                Short.parseShort(countryTable.getCountryCode(), 16),
                (short) countryTable.getTableId(),
                direction,
                false,
                Optional.of((short) extent),
                Optional.of(Short.parseShort(countryTable.getExtendedCountryCode(), 16)),
                Optional.empty(),
                Optional.empty(),
                usePrimaryInternal,
                false)),
        Optional.empty());
  }

  private static void outputResolvedLocations(final List<LinearLocation> resolvedLocations) {
    System.out.println("Resolved locations:");
    for (final LinearLocation location : resolvedLocations) {
      System.out.println(location);
    }
  }
}
