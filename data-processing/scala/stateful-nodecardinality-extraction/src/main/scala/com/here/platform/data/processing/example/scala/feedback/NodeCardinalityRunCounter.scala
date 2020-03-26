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

package com.here.platform.data.processing.example.scala.feedback

import play.api.libs.json.{Json, OFormat}

/**
  * Keeps the cardinality of nodes, the number of times compilation has run and the hash code
  * of the stored cardinalities for each partition
  **/
case class NodeCardinalityRunCounter(cardinality: Map[String, Int], updatesCount: Int, hash: Int) {

  /**
    * Serializes a [[NodeCardinalityRunCounter]] to a byte array
    *
    * @return the serialized object
    */
  def toByteArray: Array[Byte] =
    Json.toBytes(
      Json.toJson(this)
    )
}

object NodeCardinalityRunCounter {

  implicit val format: OFormat[NodeCardinalityRunCounter] = Json.format[NodeCardinalityRunCounter]

  /**
    * Deserializes a [[NodeCardinalityRunCounter]] instance.
    *
    * @param inputData to deserialize
    * @return created instance
    **/
  def fromByteArray(inputData: Array[Byte]): NodeCardinalityRunCounter =
    Json.parse(inputData).as[NodeCardinalityRunCounter]

}
