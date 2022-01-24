/*
 * Copyright (C) 2017-2022 HERE Europe B.V.
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

package com.here.platform.data.processing.example.scala.geometry.lifter

import com.here.platform.data.processing.driver.config.CompleteConfig
import com.here.platform.data.processing.driver.runner.pipeline.PipelineRunner
import com.here.platform.data.processing.driver.{Driver, DriverBuilder, DriverContext}
import net.ceedubs.ficus.Ficus._ // import .as[]
import net.ceedubs.ficus.readers.ArbitraryTypeReader._ // import value reader for case classes

object Main extends PipelineRunner {
  override val applicationVersion: String = "1.0.0"

  val TaskId = "geometryliftercompiler"

  override def setupDriver(
                           // design pattern: Template Method
                           // define your config schema and values according to the schema in other files;
                           // concrete config will be provided here
                           completeConfig: CompleteConfig,
                           // concrete params
                           context: DriverContext): Driver = {
    // single entry point to access Spark concrete driver and Data Processing library core features
    // defining your compiler in terms of providing your concrete Compiler inside Pipeline inside Driver
    val driverBuilder = new DriverBuilder(context)

    // build pipeline task in correspondence to the supplied driverBuilder
    // task ID should contain only lowercase letters and numbers
    val taskBuilder = driverBuilder.newTaskBuilder(TaskId)

    // instance of your concrete compiler class to be inserted into pipeline
    val compiler = new Compiler(context, completeConfig.compilerConfig.as[CompilerConfig])

    driverBuilder
      .addTask( // constructing a Pipeline Task (of the correct class and doing this right)

        // design pattern: Builder
        // concrete method -- depends on the sort of your compiler
        taskBuilder
          .withDirectMToNCompiler(compiler) // everything is parameterized with type of your data
          .build())
      .build() // and creating a driver instance which goes into the framework and Spark cluster
  }
}
