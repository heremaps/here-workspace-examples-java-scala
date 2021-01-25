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

package com.here.platform.data.validation.example.quickstart.assessment.java;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Retriever;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.validation.core.java.assessment.context.DirectFeatureLoader;
import com.here.platform.pipeline.logging.LogContext;
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.Metrics.Metric;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Extracts metric data and candidate tile counts
public class FeatureLoader implements DirectFeatureLoader<Data> {

  private static final Map<String, Set<String>> layers = createLayersMap();

  private static Map<String, Set<String>> createLayersMap() {
    Map<String, Set<String>> theMap = new HashMap<String, Set<String>>();

    // TODO: Declare catalog and layer IDs in their own object, as they are expected to be in sync
    theMap.put("quickstartmetrics", new HashSet<>(Collections.singletonList("metrics-result")));
    theMap.put("quickstartinput", new HashSet<>(Collections.singletonList("linesegments")));

    return theMap;
  }

  private static final long serialVersionUID = 2982807509948780844L;

  private Retriever metricsRetriever;

  FeatureLoader(DriverContext context) {
    metricsRetriever = context.inRetriever("quickstartmetrics");
  }

  @Override
  public Map<String, Set<String>> inLayers() {
    return layers;
  }

  @Override
  // Extracts the actual error metric or tile count from an input partition
  public Data loadFeatures(Pair<Key, Meta> in, LogContext logContext) {
    try {
      String catalogId = in.getKey().catalog();
      switch (catalogId) {
        case "quickstartmetrics":
          return new Data(
              Metric.parseFrom(metricsRetriever.getPayload(in.getKey(), in.getValue()).content()));
        case "quickstartinput":
          return new Data(1);
        default:
          throw new IllegalStateException("Unexpected input catalog: " + catalogId);
      }
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException(e);
    }
  }
}
