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

package com.here.platform.data.validation.example.quickstart.testing.java;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Retriever;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.validation.core.java.testing.context.MapGroupFeatureLoader;
import com.here.platform.pipeline.logging.LogContext;
import com.here.platform.schema.data.validation.example.quickstart.input.v1.Schema.LineSegments;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Extracts line segment geometry from input catalog
public class FeatureLoader implements MapGroupFeatureLoader<LineSegments> {

  // TODO: Declare catalog and layer IDs in their own object, as they are expected to be in sync
  private static final Map<String, Set<String>> layers =
      Collections.singletonMap(
          "quickstartinput", new HashSet<>(Collections.singletonList("linesegments")));
  private static final long serialVersionUID = 4185640251981133878L;

  private Retriever retriever;

  FeatureLoader(DriverContext context) {
    retriever = context.inRetriever(layers.keySet().iterator().next());
  }

  @Override
  public Map<String, Set<String>> inLayers() {
    return layers;
  }

  @Override
  // Extracts the actual line segment from an input partition
  public LineSegments loadFeatures(Pair<Key, Meta> in, LogContext logContext) {
    try {
      return LineSegments.parseFrom(retriever.getPayload(in.getKey(), in.getValue()).content());
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException(e);
    }
  }
}
