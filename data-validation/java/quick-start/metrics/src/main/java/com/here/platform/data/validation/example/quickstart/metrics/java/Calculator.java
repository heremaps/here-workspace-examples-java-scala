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

package com.here.platform.data.validation.example.quickstart.metrics.java;

import com.here.platform.data.processing.driver.Default;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.catalog.partition.Generic;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.pipeline.logging.LogContext;
import com.here.platform.schema.data.validation.core.metrics.v1.Metrics.TestMetricSeverity;
import com.here.platform.schema.data.validation.core.testing.v1.Testing.TestResult.TestResultType;
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.Metrics.Metric;
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.Metrics.MetricSample;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

// The actual map-reduce implementation that assigns severities to the test results
public class Calculator
    implements com.here.platform.data.validation.core.java.metrics.calculators.Metric<
        Data, Metric> {

  private static final long serialVersionUID = -4545526780955027307L;

  private MetricsConfig config;

  public Calculator(MetricsConfig metricsConfig) {
    config = metricsConfig;
  }

  @Override
  public String id() {
    return "quickstartmetriccalc";
  }

  @Override
  public Iterable<Pair<Key, Data>> map(Pair<Key, Data> context, LogContext logContext) {
    // The input test result is mapped to an output partition with the name of the result.
    // In this example, all FAIL results from the input catalog go into the FAIL partition.
    Generic resultAsString =
        new Generic(context.getValue().getResult().getResult().getValue().toString());
    Key outKey = new Key(Default.OutCatalogId().name(), "metrics-result", resultAsString);
    return Collections.singletonList(new Pair<>(outKey, context.getValue()));
  }

  @Override
  public java.util.Optional<Metric> reduce(Pair<Key, Iterable<Data>> data, LogContext logContext) {

    // Aggregate all tile IDs from this type of test result. For this example, the type is always
    // FAIL.
    List<Long> tileIds =
        StreamSupport.stream(data.getValue().spliterator(), true)
            .map(Data::getTileID)
            .sorted()
            .collect(Collectors.toList());

    if (!tileIds.isEmpty()) {
      TestMetricSeverity.SeverityType severityType = TestMetricSeverity.SeverityType.CRITICAL;

      if (data.getKey().partition().toString().equals(TestResultType.PASS.toString())) {
        // PASS results are considered Severity NONE
        if (config.getWriteSeverityNoneSamples())
          severityType = TestMetricSeverity.SeverityType.NONE;
        else return java.util.Optional.empty();
      }

      // In this example, the calculator assigns a CRITICAL severity for all non-PASS results,
      // and collects all the tile IDs for each result type in the input catalog.
      MetricSample metricSample =
          MetricSample.newBuilder()
              .addAllTileIds(tileIds)
              .setSeverity(
                  TestMetricSeverity.newBuilder().setMetricid(id()).setValue(severityType).build())
              .build();
      return java.util.Optional.of(
          Metric.newBuilder().addAllMetrics(Collections.singletonList(metricSample)).build());
    }

    // If no tile IDs were encountered, there is nothing to output
    return java.util.Optional.empty();
  }
}
