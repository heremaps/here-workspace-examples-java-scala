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

package com.here.platform.data.validation.example.quickstart.comparison.java;

import com.here.platform.data.processing.java.compiler.InputLayers;
import com.here.platform.data.processing.java.compiler.OutputLayers;
import com.here.platform.data.validation.core.java.comparison.ContextHelper;
import java.util.*;

class LayerDefs implements OutputLayers, InputLayers {

  static final String hereTiledOutputLayer = "heretile-comparison-results";
  static final String genericOutLayer = "generic-comparison-results";
  static final String hereTiledInputLayer = "linesegments";
  static final String genericInputLayer = "state";

  private static final HashMap<String, Set<String>> inMap =
      new HashMap<String, Set<String>>() {
        private static final long serialVersionUID = 3736103642496106633L;

        {
          put(
              ContextHelper.CANDIDATECATID().toString(),
              makeSet(hereTiledInputLayer, genericInputLayer));
        }
      };

  @Override
  public Set<String> outLayers() {
    return makeSet(genericOutLayer, hereTiledOutputLayer);
  }

  private static HashSet<String> makeSet(String... strings) {
    return new HashSet<>(Arrays.asList(strings));
  }

  @Override
  public Map<String, Set<String>> inLayers() {
    return inMap;
  }
}
