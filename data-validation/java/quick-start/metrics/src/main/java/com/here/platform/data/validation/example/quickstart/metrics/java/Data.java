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

import com.here.platform.schema.data.validation.example.quickstart.testing.v1.Testing.Result;
import java.io.Serializable;

// Intermediate data extracted from test results.
public class Data implements Serializable {
  private static final long serialVersionUID = -808338415090980652L;
  private Result result;
  private long tileID;

  public Data(Result result, long tileID) {
    this.result = result;
    this.tileID = tileID;
  }

  public Result getResult() {
    return result;
  }

  public long getTileID() {
    return tileID;
  }
}
