/*
 * Copyright (C) 2017-2025 HERE Europe B.V.
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

import com.here.platform.location.tpeg2.BinaryMarshaller;
import com.here.platform.location.tpeg2.BinaryMarshallers;
import com.here.platform.location.tpeg2.XmlMarshaller;
import com.here.platform.location.tpeg2.XmlMarshallers;
import com.here.platform.location.tpeg2.olr.OpenLRLocationReference;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class ConvertTpeg2ContainerExample {

  public static void main(final String[] args) {
    // Get an XML (un)marshaller for the `OpenLRLocationReference`.
    // The `XmlMarshallers` class contains factory methods for those
    // TPEG2 types that can be used as top-level elements in an XML document.

    final XmlMarshaller<OpenLRLocationReference> xmlMarshaller =
        XmlMarshallers.openLRLocationReference();

    final OpenLRLocationReference ref = xmlMarshaller.unmarshall(getResource("/olr-reference.xml"));

    // There is a reasonable `toString()` implementation.
    System.out.println(ref);

    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    // Get a binary marshaller for `OpenLRLocationReference`.
    // The `BinaryMarshallers` class contains factory methods for those
    // TPEG2 types that can be serialized independently.
    final BinaryMarshaller<OpenLRLocationReference> binaryMarshaller =
        BinaryMarshallers.openLRLocationReference();

    binaryMarshaller.marshall(ref, outputStream);
  }

  private static InputStream getResource(final String name) {
    return ConvertTpeg2ContainerExample.class.getResourceAsStream(name);
  }
}
