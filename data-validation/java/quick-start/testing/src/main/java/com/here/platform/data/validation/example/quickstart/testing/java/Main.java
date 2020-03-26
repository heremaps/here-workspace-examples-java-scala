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

import com.here.platform.data.processing.build.BuildInfo;
import com.here.platform.data.processing.driver.runner.pipeline.java.PipelineRunner;
import com.here.platform.data.processing.java.driver.DriverBuilder;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.driver.config.CompleteConfig;
import com.here.platform.data.validation.core.java.testing.builder.TaskBuilder;

// This is the main entry point for the testing pipeline of the quickstart example.
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
   * @return a fully-configured driver builder
   */
  public static final DriverBuilder configureCompiler(
      CompleteConfig completeConfig, DriverContext context, DriverBuilder builder) {
    com.here.platform.data.validation.core.java.testing.builder.DriverBuilder testingDriverBuilder =
        new com.here.platform.data.validation.core.java.testing.builder.DriverBuilder(context);

    TestingConfig testingConfig = completeConfig.getCompilerConfig(TestingConfig.class);

    // The testing pipeline consists of a single processor which contains a single family with a
    // single validator,
    // and a feature loader to extract the data to be validated from the input catalog.
    Validator validator =
        new Validator(new TestFamily(new TestCase(testingConfig)), new FeatureLoader(context));

    TaskBuilder taskBuilder = testingDriverBuilder.newTestingTaskBuilder();

    // The validator is a map-group compiler which validates the contents of individual input
    // partitions without
    // needing any additional context from external partitions or other catalogs.
    return builder.addTask(taskBuilder.withValidator(validator).build());
  }

  public static void main(String... args) {
    new PipelineRunner() {
      @Override
      public String applicationVersion() {
        return Main.applicationVersion();
      }

      @Override
      public final DriverBuilder configureCompiler(
          CompleteConfig completeConfig, DriverContext context, DriverBuilder builder) {
        return Main.configureCompiler(completeConfig, context, builder);
      }
    }.main(args);
  }
}
