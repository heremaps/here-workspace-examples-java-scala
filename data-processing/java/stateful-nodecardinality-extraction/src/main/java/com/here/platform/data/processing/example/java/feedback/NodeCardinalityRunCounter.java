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

package com.here.platform.data.processing.example.java.feedback;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Keeps the cardinality of nodes, the number of times compilation has run and the hash code of the
 * stored cardinalities for each partition
 */
class NodeCardinalityRunCounter {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Constructor */
  @JsonCreator
  NodeCardinalityRunCounter(
      @JsonProperty("cardinality") Map<String, Integer> cardinality,
      @JsonProperty("updatesCount") int updatesCount,
      @JsonProperty("hash") int hash) {
    this.cardinality = cardinality;
    this.updatesCount = updatesCount;
    this.hash = hash;
  }

  /**
   * Serializes a [[NodeCardinalityRunCounter]] to a byte array
   *
   * @return the serialized object
   */
  byte[] toByteArray() {
    try {
      return MAPPER.writeValueAsString(this).getBytes(StandardCharsets.UTF_8);
    } catch (JsonProcessingException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Deserializes a [[NodeCardinalityRunCounter]] instance.
   *
   * @param inputData to deserialize
   * @return created instance
   */
  static NodeCardinalityRunCounter fromByteArray(byte[] inputData) {
    String previousJsonString = new String(inputData, StandardCharsets.UTF_8);
    try {
      return MAPPER.readValue(previousJsonString, NodeCardinalityRunCounter.class);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  int hash() {
    return hash;
  }

  int count() {
    return updatesCount;
  }

  @JsonProperty private final Map<String, Integer> cardinality;

  @JsonProperty private final int updatesCount;

  @JsonProperty private final int hash;
}
