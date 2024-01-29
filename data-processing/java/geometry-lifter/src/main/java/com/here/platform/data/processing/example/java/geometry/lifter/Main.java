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

package com.here.platform.data.processing.example.java.geometry.lifter;

import com.here.platform.data.processing.driver.runner.pipeline.java.PipelineRunner;
import com.here.platform.data.processing.java.driver.*;
import com.here.platform.data.processing.java.driver.config.CompleteConfig;

/** Class executed on the Spark cluster. */
public class Main {
  public static void main(String[] args) {
    new MainPipelineRunner().main(args);
  }

  // Boilerplate code for running your batch processor,
  // most batch processors have very similar code here.
  public static class MainPipelineRunner

      // Major class that resembles something that is executed.
      extends PipelineRunner {

    @Override
    public String applicationVersion() {
      return "1.0.0";
    }

    @Override
    public DriverBuilder configureCompiler( // design pattern: Template Method
        // Specify your config schema and values according to the schema in other files;
        // concrete config is provided here
        CompleteConfig completeConfig,

        // concrete params
        DriverContext context,

        // single entry point to access Spark concrete driver and data processing core features
        // defining your batch processor in terms of providing your concrete Compiler inside a Task
        // inside the Driver
        DriverBuilder builder) {

      // build task for the supplied driver builder
      // The task ID should contain only lowercase letters and numbers. The ID is
      // used it identify a task in the logs.
      TaskBuilder taskBuilder = builder.newTaskBuilder("geometryliftercompiler");

      // instance of your concrete batch processor class to be inserted into task
      Compiler theCompiler =
          new Compiler(
              context, // pass the context further
              completeConfig.getCompilerConfig(
                  CompilerConfig.class)); // and provide the values of config

      return builder.addTask(
          taskBuilder // constructing a Task (of the correct class)
              .withDirectMToNCompiler(
                  theCompiler,
                  IntermediateData.class) // everything is parameterized with the type of your data
              .build()); // and creating a driver instance that goes into the framework and Spark
      // cluster
    }
  }
}
