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

package com.here.platform.data.validation.example.quickstart.testing.scala

import com.here.platform.data.processing.build.BuildInfo
import com.here.platform.data.processing.driver.config.CompleteConfig
import com.here.platform.data.processing.driver.runner.pipeline.PipelineRunner
import com.here.platform.data.processing.driver.DriverContext
import com.here.platform.data.validation.core.testing.builder.{
  DriverBuilder,
  DriverSetupWithBuilder
}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

/** Main command-line handler to launch the [[Validator]] pipeline
  *
  */
object Main extends PipelineRunner with DriverSetupWithBuilder {

  def applicationVersion: String = BuildInfo.version

  def configureCompiler(completeConfig: CompleteConfig,
                        context: DriverContext,
                        builder: DriverBuilder): builder.type = {

    val testingConfig = completeConfig.compilerConfig.as[TestingConfig]
    val validator = Validator(context, testingConfig)
    val task =
      builder.newTestingTaskBuilder().withValidator(validator).build()
    builder.addTask(task)
  }
}
