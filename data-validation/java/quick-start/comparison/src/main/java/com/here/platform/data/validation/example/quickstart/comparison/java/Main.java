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

package com.here.platform.data.validation.example.quickstart.comparison.java;

import com.here.platform.data.processing.build.BuildInfo;
import com.here.platform.data.processing.driver.runner.pipeline.java.PipelineRunner;
import com.here.platform.data.processing.java.driver.DriverBuilder;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.driver.config.CompleteConfig;
import com.here.platform.data.validation.core.java.comparison.ContextHelper;
import com.here.platform.data.validation.core.java.comparison.builder.TaskBuilder;
import com.here.platform.data.validation.core.java.comparison.metadiff.grouped.GroupedComparator;

public class Main {

  public static String applicationVersion() {
    return BuildInfo.version();
  }

  public static final DriverBuilder configureCompiler(
      CompleteConfig completeConfig, DriverContext context, DriverBuilder builder) {
    com.here.platform.data.validation.core.java.comparison.builder.DriverBuilder
        comparisonDriverBuilder =
            new com.here.platform.data.validation.core.java.comparison.builder.DriverBuilder(
                context);

    final LayerDefs layerDefs = new LayerDefs();
    final ContextHelper helper = ContextHelper.apply(context, layerDefs.inLayers());
    GroupedComparator comparator =
        new GroupedComparator(helper, new QuickstartComparisonByPartition(helper)) {
          @Override
          public java.util.Set<String> outLayers() {
            return layerDefs.outLayers();
          }

          @Override
          public java.util.Map<String, java.util.Set<String>> inLayers() {
            return layerDefs.inLayers();
          }
        };

    TaskBuilder taskBuilder = comparisonDriverBuilder.newComparisonTaskBuilder();

    return builder.addTask(taskBuilder.withComparator("quickstart", comparator).build());
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
