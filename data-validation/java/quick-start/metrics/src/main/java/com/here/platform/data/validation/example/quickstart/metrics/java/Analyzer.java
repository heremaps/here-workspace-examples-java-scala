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

import com.here.platform.data.validation.core.java.metrics.analyzers.MapGroupAnalyzer;
import com.here.platform.data.validation.core.java.metrics.calculators.families.CalculatorFamily;
import com.here.platform.data.validation.core.java.metrics.context.MapGroupFeatureLoader;
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.Metrics.Metric;

// The map-group processor that assigns metrics from the test results.
public class Analyzer extends MapGroupAnalyzer<Data, Metric> {

  private static final long serialVersionUID = 4632882345395943853L;

  public Analyzer(
      CalculatorFamily<Data, Metric> mapReduceFamily, MapGroupFeatureLoader<Data> featureLoader) {
    super(mapReduceFamily, featureLoader);
  }
}
