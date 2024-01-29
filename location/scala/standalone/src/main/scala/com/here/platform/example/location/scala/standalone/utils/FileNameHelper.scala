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

package com.here.platform.example.location.scala.standalone.utils

import java.io.File

object FileNameHelper {
  def exampleJsonFileFor(obj: Any): File = {
    val outputDir = new File(System.getProperty("java.io.tmpdir"), "example_output")
    outputDir.mkdir()

    val demangledClassName = obj.getClass.getCanonicalName.replace("$", "")

    new File(outputDir, s"$demangledClassName.json")
  }
}
