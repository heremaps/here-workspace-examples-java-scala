/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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

import static java.util.Arrays.asList;

import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.core.graph.javadsl.PropertyMap;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.inmemory.graph.javadsl.Direction;
import com.here.platform.location.integration.optimizedmap.OptimizedMap;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.location.integration.optimizedmap.geospatial.HereMapContentReference;
import com.here.platform.location.integration.optimizedmap.graph.javadsl.PropertyMaps;
import com.here.platform.location.referencing.LinearLocation;
import com.here.platform.location.referencing.LocationReferenceCreator;
import com.here.platform.location.referencing.javadsl.LocationReferenceCreators;
import com.here.platform.location.referencing.olr.OlrPrettyPrinter;
import com.here.platform.location.tpeg2.XmlMarshallers;
import com.here.platform.location.tpeg2.javadsl.BinaryMarshallers;
import com.here.platform.location.tpeg2.olr.LinearLocationReference;
import com.here.platform.location.tpeg2.olr.OpenLRLocationReference;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This example shows how to take a path given as HERE Map Content references and create an OLR
 * reference from it.
 */
public final class OlrCreateReferenceFromHmcSegmentsExample {

  public static void main(final String[] args) throws FileNotFoundException {
    final BaseClient baseClient = BaseClientJava.instance();
    final OptimizedMapLayers optimizedMap =
        OptimizedMapCatalog.from(OptimizedMap.v2.HRN)
            .usingBaseClient(baseClient)
            .newInstance()
            .version(769L);

    try {
      final List<String> segmentStrings =
          asList(
              "23618402/192867771+",
              "23618402/190690930-",
              "23618402/101695256-",
              "23618402/87970589-",
              "23618402/87981292-",
              "23618402/87981291-",
              "23618402/779616973-",
              "23618402/779617453-",
              "23618402/88153132-",
              "23618402/78021962-",
              "23618402/78021961-",
              "23618402/82451780-",
              "23618402/86519396-",
              "23618402/190459526-",
              "23618359/94455601-",
              "23618359/94823702-",
              "23618359/94476820-",
              "23618359/103954050-",
              "23618359/95647469-",
              "23618359/157157401-",
              "23618359/783973312-",
              "23618359/783976431-",
              "23618359/484177650+",
              "23618359/81626947-",
              "23618359/182595729-",
              "23618359/192256710-",
              "23618359/105169214+",
              "23618359/102022769-",
              "23618359/78758076-",
              "23618359/93979650-",
              "23618359/192336062-",
              "23618359/100422521-",
              "23618359/83634003-",
              "23618359/80415973-",
              "23618359/805793032-",
              "23618359/791530650+",
              "23618359/203894554-",
              "23618359/77179756-",
              "23618359/77833225-",
              "23618359/195404089-",
              "23618359/86896561-",
              "23618359/95356471-",
              "23618359/87921793-",
              "23618359/196013524+",
              "23618359/84734148-",
              "23618359/208151869-",
              "23618359/159034659-",
              "23618359/98034107-",
              "23618359/91955672+",
              "23618359/85912316+",
              "23618359/605536826+",
              "23618359/788742068+",
              "23618359/195423613-",
              "23618359/99880420+",
              "23618359/88072134+",
              "23618359/79293249+",
              "23618359/182750790-",
              "23618359/203288051-");

      final List<HereMapContentReference> segments =
          segmentStrings
              .stream()
              .map(OlrCreateReferenceFromHmcSegmentsExample::parseHmcRef)
              .collect(Collectors.toList());

      final PropertyMap<HereMapContentReference, Vertex> hmcToVertex =
          new PropertyMaps(optimizedMap).hereMapContentReferenceToVertex();

      final List<Vertex> vertices =
          segments.stream().map(hmcToVertex::get).collect(Collectors.toList());

      final LinearLocation location = new LinearLocation(vertices, 0, 1);

      final LocationReferenceCreator<LinearLocation, LinearLocationReference> creator =
          new LocationReferenceCreators(optimizedMap).olrLinear();

      final LinearLocationReference reference = creator.create(location);

      // For debugging purposes there is a prettyPrint function.
      System.out.println(OlrPrettyPrinter.prettyPrint(reference));

      // This is how to serialize the reference to XML.
      XmlMarshallers.openLRLocationReference()
          .marshall(
              new OpenLRLocationReference("1.1", reference, Optional.empty()),
              new FileOutputStream("olr.xml"));

      // This is how to serialize the reference to binary.
      BinaryMarshallers.openLRLocationReference()
          .marshall(
              new OpenLRLocationReference("1.1", reference, Optional.empty()),
              new FileOutputStream("olr.bin"));
    } finally {
      baseClient.shutdown();
    }
  }

  private static HereMapContentReference parseHmcRef(final String hmcRef) {
    final String[] components = hmcRef.substring(0, hmcRef.length() - 1).split("/");

    return new HereMapContentReference(
        components[0],
        "here:cm:segment:" + components[1],
        hmcRef.endsWith("-") ? Direction.BACKWARD : Direction.FORWARD);
  }
}
