/*
 * Copyright (C) 2017-2022 HERE Europe B.V.
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

import static java.util.Collections.singletonList;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.core.geospatial.GeoCoordinate;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.geospatial.javadsl.ProximitySearches;
import com.here.platform.location.referencing.BidirectionalLinearLocation;
import com.here.platform.location.referencing.LinearLocation;
import com.here.platform.location.referencing.LocationReferenceCreator;
import com.here.platform.location.referencing.LocationReferenceResolver;
import com.here.platform.location.referencing.javadsl.LocationReferenceCreators;
import com.here.platform.location.referencing.javadsl.LocationReferenceResolvers;
import com.here.platform.location.tpeg2.XmlMarshallers;
import com.here.platform.location.tpeg2.lrc.LocationReferencingContainer;
import com.here.platform.location.tpeg2.tmc.TMCLocationReference;

/**
 * Create and resolve a TMC reference.
 *
 * <p>The example searches for a well-known vertex that is covered by TMC, and uses that to create a
 * TMC reference. The reference is later resolved and outputted for comparison.
 */
public final class TmcCreateAndResolveExample {
  private static final GeoCoordinate coordinateInFriedenstrasse =
      new GeoCoordinate(52.527111, 13.427079);

  public static void main(final String[] args) {
    final BaseClient baseClient = BaseClientJava.instance();

    try {
      final OptimizedMapLayers optimizedMap =
          OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
              .usingBaseClient(baseClient)
              .newInstance()
              .version(1293L);

      // Define a location that is covered by TMC
      final Vertex vertexInFriedenstrasse =
          new ProximitySearches(optimizedMap)
              .vertices()
              .search(coordinateInFriedenstrasse, 10)
              .iterator()
              .next()
              .getElement();

      final LinearLocation locationInFriedenstrasse =
          new LinearLocation(singletonList(vertexInFriedenstrasse), 0, 1);

      // Create a reference for that location
      final LocationReferenceCreator<LinearLocation, TMCLocationReference> tmcRefCreator =
          new LocationReferenceCreators(optimizedMap).tmc();

      final TMCLocationReference tmcRef = tmcRefCreator.create(locationInFriedenstrasse);

      // Resolve the newly created reference
      final LocationReferenceResolver<TMCLocationReference, BidirectionalLinearLocation>
          tmcRefResolver = new LocationReferenceResolvers(optimizedMap).tmc();

      final BidirectionalLinearLocation resolvedLocation = tmcRefResolver.resolve(tmcRef);

      // Visualize the original location, the reference created, and the resolved location
      visualizeResults(locationInFriedenstrasse, tmcRef, resolvedLocation.getLocation());
    } finally {
      baseClient.shutdown();
    }
  }

  private static void visualizeResults(
      final LinearLocation inputLocation,
      final TMCLocationReference tmcRef,
      final LinearLocation resolvedLocation) {
    System.out.println("Input location: " + inputLocation);
    System.out.println("Resolved location: " + resolvedLocation);
    System.out.println("Location reference:");

    XmlMarshallers.locationReferencingContainer()
        .marshall(new LocationReferencingContainer(singletonList(tmcRef)), System.out);
  }
}
