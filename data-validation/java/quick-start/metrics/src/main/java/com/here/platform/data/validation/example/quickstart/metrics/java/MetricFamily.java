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

package com.here.platform.data.validation.example.quickstart.metrics.java;

import com.here.platform.data.validation.core.java.metrics.calculators.families.SingleCalculator;
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.Metrics.Metric;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// Single map-reduce family to assign severities to the test results
public class MetricFamily extends SingleCalculator<Data, Metric> {

  private static final long serialVersionUID = 6198974501139700824L;

  MetricFamily(Calculator mapReduce) {
    super(mapReduce);
  }

  @Override
  public String id() {
    return "quickstartmetricfamily";
  }

  @Override
  public byte[] toByteArray(Metric metric) {
    return metric.toByteArray();
  }

  @Override
  public Set<String> outLayers() {
    // TODO: Declare catalog and layer IDs in their own object, as they are expected to be in sync
    return new HashSet<>(Collections.singletonList("metrics-result"));
  }
}
