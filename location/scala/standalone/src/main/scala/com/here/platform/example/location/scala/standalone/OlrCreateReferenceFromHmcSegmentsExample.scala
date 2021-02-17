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

import java.io.FileOutputStream

import com.here.platform.location.dataloader.core.caching.CacheManager
import com.here.platform.location.dataloader.standalone.StandaloneCatalogFactory
import com.here.platform.location.inmemory.graph.{Backward, Forward}
import com.here.platform.location.integration.heremapcontent.PartitionId
import com.here.platform.location.integration.optimizedmap.OptimizedMap
import com.here.platform.location.integration.optimizedmap.geospatial.{
  HereMapContentReference,
  SegmentId
}
import com.here.platform.location.integration.optimizedmap.graph.PropertyMaps
import com.here.platform.location.referencing.olr.OlrPrettyPrinter
import com.here.platform.location.referencing.{LinearLocation, LocationReferenceCreators}
import com.here.platform.location.tpeg2.olr.OpenLRLocationReference
import com.here.platform.location.tpeg2.{BinaryMarshallers, XmlMarshallers}

/** This example shows how to take a path given as HERE Map Content references
  * and create an OLR reference from it.
  */
object OlrCreateReferenceFromHmcSegmentsExample extends App {
  val segmentStrings = Seq(
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
    "23618359/203288051-"
  )

  val catalogFactory = new StandaloneCatalogFactory()

  try {
    val cacheManager = CacheManager.withLruCache()
    val optimizedMap = catalogFactory.create(OptimizedMap.v2.HRN, 769L)

    val segments = segmentStrings.map(parseHmcRef)

    val hmcToVertex = PropertyMaps.hereMapContentReferenceToVertex(optimizedMap, cacheManager)

    val vertices = segments.map(hmcToVertex(_))

    val location = LinearLocation(vertices)

    val creator = LocationReferenceCreators.olrLinear(optimizedMap, cacheManager)

    val reference = creator.create(location)

    // For debugging purposes there is a prettyPrint function.
    println(OlrPrettyPrinter.prettyPrint(reference))

    // This is how to serialize the reference to XML.
    XmlMarshallers.openLRLocationReference
      .marshall(OpenLRLocationReference("1.1", reference, None), new FileOutputStream("olr.xml"))

    // This is how to serialize the reference to binary.
    BinaryMarshallers.openLRLocationReference
      .marshall(OpenLRLocationReference("1.1", reference, None), new FileOutputStream("olr.bin"))
  } finally {
    catalogFactory.terminate()
  }

  def parseHmcRef(hmcRef: String): HereMapContentReference = {
    val Array(p, s) = hmcRef.dropRight(1).split("/")
    HereMapContentReference(
      partitionId = PartitionId(p),
      segmentId = SegmentId(s"here:cm:segment:$s"),
      direction = hmcRef.last match {
        case '+' => Forward
        case '-' => Backward
      }
    )
  }
}
