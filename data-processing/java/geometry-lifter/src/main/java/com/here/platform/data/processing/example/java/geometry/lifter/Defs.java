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

import java.util.*;

class Defs {

  /** Input and output layers configuration for the geometry compiler */
  static class LayerDefs {

    static final Map<String, Set<String>> inLayers =
        Collections.singletonMap(In.RIB_CATALOG, Collections.singleton(In.TOPOLOGY_LAYER));

    static final Set<String> outLayers = Collections.singleton(Out.LIFTED_TOPOLOGY_GEOMETRY_LAYER);

    private LayerDefs() {}
  }

  /** Input catalog definitions */
  static class In {

    static final String RIB_CATALOG = "rib";
    static final String TOPOLOGY_LAYER = "topology-geometry";

    private In() {}
  }

  /** Output catalog definitions */
  static class Out {

    static final String LIFTED_TOPOLOGY_GEOMETRY_LAYER = "lifted-topology-geometry";

    private Out() {}
  }

  private Defs() {}
}
