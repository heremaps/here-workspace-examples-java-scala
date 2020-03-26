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

import com.here.platform.data.processing.build.BuildInfo;
import com.here.platform.data.processing.driver.runner.pipeline.java.PipelineRunner;
import com.here.platform.data.processing.java.driver.DriverBuilder;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.driver.config.CompleteConfig;
import com.here.platform.data.validation.core.java.metrics.builder.TaskBuilder;

// This is the main entry point for the metrics pipeline of the quickstart example.
public class Main {

  public static String applicationVersion() {
    return BuildInfo.version();
  }

  /**
   * Template method that configures the compiler
   *
   * @param completeConfig the full configuration
   * @param context the driver context
   * @param builder the driver builder
   * @param logContext the logging context for the driver
   * @return a fully-configured driver builder
   */
  public static final DriverBuilder configureCompiler(
      CompleteConfig completeConfig, DriverContext context, DriverBuilder builder) {
    com.here.platform.data.validation.core.java.metrics.builder.DriverBuilder metricsDriverBuilder =
        new com.here.platform.data.validation.core.java.metrics.builder.DriverBuilder(context);
    TaskBuilder taskBuilder = metricsDriverBuilder.newMetricsTaskBuilder();

    MetricsConfig metricsConfig = completeConfig.getCompilerConfig(MetricsConfig.class);

    // The metrics pipeline consists of a single analyzer which contains a single family with a
    // single calculator,
    // and a feature loader to extract the test results from the input catalog.
    Analyzer analyzer =
        new Analyzer(new MetricFamily(new Calculator(metricsConfig)), new FeatureLoader(context));

    // The analyzer is a map-group compiler which assigns severities to test results without needing
    // any
    // additional context from external partitions or other catalogs.
    return builder.addTask(taskBuilder.withAnalyzer(analyzer).build());
  }

  public static void main(String... args) {
    new PipelineRunner() {

      @Override
      public String applicationVersion() {
        return Main.applicationVersion();
      }

      @Override
      public DriverBuilder configureCompiler(
          CompleteConfig completeConfig, DriverContext context, DriverBuilder builder) {
        return Main.configureCompiler(completeConfig, context, builder);
      }
    }.main(args);
  }
}
