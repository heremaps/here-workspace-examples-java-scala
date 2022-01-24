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

package com.here.platform.data.processing.example.java.pedestrian.protobuf;

import java.io.Serializable;

/** Compiler specific configuration (JavaBean). */
public class CompilerConfig implements Serializable {

  private int outputLevel;

  /** Empty constructor. */
  public CompilerConfig() {}

  /** @return The output layer's zoom level. */
  public int getOutputLevel() {
    return outputLevel;
  }

  /** Sets the output layer's zoom level. */
  public void setOutputLevel(int outputLevel) {
    this.outputLevel = outputLevel;
  }
}
