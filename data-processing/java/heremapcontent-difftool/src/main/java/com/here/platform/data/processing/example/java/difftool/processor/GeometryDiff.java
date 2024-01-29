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

package com.here.platform.data.processing.example.java.difftool.processor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Class that represents data model for output of the Example mapped to Json format using Jackson
 * JSON toolkit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class GeometryDiff {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Constructor */
  @JsonCreator
  GeometryDiff(
      @JsonProperty("modifiedSegments") List<String> modifiedSegments,
      @JsonProperty("removedSegments") List<String> removedSegments,
      @JsonProperty("addedSegments") List<String> addedSegments) {
    this.modifiedSegments = modifiedSegments;
    this.removedSegments = removedSegments;
    this.addedSegments = addedSegments;
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

  @JsonProperty private final List<String> modifiedSegments;
  @JsonProperty private final List<String> removedSegments;
  @JsonProperty private final List<String> addedSegments;
}
