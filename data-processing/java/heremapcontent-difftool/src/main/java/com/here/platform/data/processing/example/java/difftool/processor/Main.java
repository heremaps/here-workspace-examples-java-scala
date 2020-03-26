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

package com.here.platform.data.processing.example.java.difftool.processor;

import com.here.platform.data.processing.driver.runner.pipeline.java.PipelineRunner;
import com.here.platform.data.processing.java.driver.DriverBuilder;
import com.here.platform.data.processing.java.driver.DriverContext;
import com.here.platform.data.processing.java.driver.DriverTask;
import com.here.platform.data.processing.java.driver.TaskBuilder;
import com.here.platform.data.processing.java.driver.config.CompleteConfig;

/** Class that will be executed on Spark cluster */
public class Main {
  public static void main(String[] args) {
    new MainPipelineRunner().main(args);
  }

  public static class MainPipelineRunner extends PipelineRunner {

    @Override
    public String applicationVersion() {
      return "1.0.0";
    }

    private final String TASK_ID = "difftool";

    @Override
    public DriverBuilder configureCompiler(
        // design pattern: Template Method
        // define your config schema and values according to the schema in other files;
        // concrete config will be provided here
        CompleteConfig completeConfig,

        // concrete params
        DriverContext context,

        // single entry point to access Spark concrete driver and Data Processing core features
        // defining your batch processor in terms of providing your concrete Compiler inside Task
        // inside Driver
        DriverBuilder builder) {

      // build task in correspondence to the supplied driver builder
      // task ID should contain only lowercase letters and numbers
      TaskBuilder taskBuilder = builder.newTaskBuilder(TASK_ID);

      // instance of your concrete batch processor class to be inserted into task
      Compiler theCompiler = new Compiler(context /*pass the context further*/);

      // constructing a Task (of the correct class and doing this right)
      DriverTask currentTask =
          taskBuilder.withDirectMToNCompiler(theCompiler, IntermediateData.class).build();

      return builder.addTask(
          currentTask); // and creating a driver instance which goes into the framework and Spark
      // cluster
    }
  }
}
