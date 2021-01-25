/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package com.here.platform.example.location.scala.standalone

import com.here.platform.location.tpeg2.sfw.TPEGDocument
import com.here.platform.location.tpeg2.tec.TECMessage
import com.here.platform.location.tpeg2.{Tpeg2Messages, XmlMarshallers}

/* Loads a TPEG2 document and extracts only the relevant messages and references from it.
 */
object ExtractSpecificTpeg2MessagesExample extends App {
  // Load a document
  val document: TPEGDocument =
    XmlMarshallers.tpegDocument.unmarshall(getResource("/tpeg-document.xml"))

  // Extract TEC Messages
  println("TEC Messages:")
  val tecMessages: Seq[TECMessage] = Tpeg2Messages(document).filterTecMessages.applicationMessages

  tecMessages.foreach(tecMessage => println(s"  - $tecMessage"))

  // Extract TFP messages with TMC references
  println("TFP messages with TMC references:")
  val tfpMessagesWithTmcReferences =
    Tpeg2Messages(document).filterTfpMessages.filterHavingTmcReferences.toSeq

  tfpMessagesWithTmcReferences.foreach { tfpOlrMessage =>
    println(s"  - ${tfpOlrMessage.applicationMessage}")
    println(s"    - ${tfpOlrMessage.locationReferences.head}")
  }

  private def getResource(name: String) =
    getClass.getResourceAsStream(name)
}
