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

package com.here.platform.example.location.java.standalone;

import com.here.platform.location.tpeg2.Tpeg2MessageView;
import com.here.platform.location.tpeg2.Tpeg2Messages;
import com.here.platform.location.tpeg2.XmlMarshallers;
import com.here.platform.location.tpeg2.sfw.TPEGDocument;
import com.here.platform.location.tpeg2.tec.TECMessage;
import com.here.platform.location.tpeg2.tfp.TFPMessage;
import com.here.platform.location.tpeg2.tmc.TMCLocationReference;
import java.io.InputStream;
import java.util.List;

public class ExtractSpecificTpeg2MessagesExample {
  public static void main(String[] args) {
    TPEGDocument document =
        XmlMarshallers.tpegDocument().unmarshall(getResource("/tpeg-document.xml"));

    System.out.println("TEC Messages:");
    List<TECMessage> tecMessages =
        Tpeg2Messages.create(document).filterTecMessages().getApplicationMessages();

    tecMessages.forEach(tecMessage -> System.out.println("  - " + tecMessage));

    System.out.println("TFP messages with TMC references:");

    List<Tpeg2MessageView<TFPMessage, TMCLocationReference>> tfpMessagesWithTmcReferences =
        Tpeg2Messages.create(document).filterTfpMessages().filterHavingTmcReferences().asList();

    tfpMessagesWithTmcReferences.forEach(
        tfpTmcMessage -> {
          System.out.println("  - " + tfpTmcMessage.getApplicationMessage());
          System.out.println("    - " + tfpTmcMessage.getLocationReferenceMethods().get(0));
        });
  }

  private static InputStream getResource(String name) {
    return ExtractSpecificTpeg2MessagesExample.class.getResourceAsStream(name);
  }
}
