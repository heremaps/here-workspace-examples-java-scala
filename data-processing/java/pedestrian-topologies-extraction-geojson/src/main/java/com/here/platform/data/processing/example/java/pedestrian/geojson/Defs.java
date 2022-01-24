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

package com.here.platform.data.processing.example.java.pedestrian.geojson;

import java.util.*;

class Defs {

  /** Input and output layers configuration for the geometry compiler */
  static class LayerDefs {

    static final Map<String, Set<String>> inLayers =
        Collections.unmodifiableMap(
            new HashMap<String, Set<String>>() {
              {
                put(
                    In.RIB_CATALOG,
                    new HashSet<String>() {
                      {
                        add(In.ROAD_LAYER);
                        add(In.TOPOLOGY_LAYER);
                      }
                    });
              }
            });

    static final Set<String> outLayers =
        Collections.unmodifiableSet(
            new HashSet<String>() {
              {
                add(Out.ROAD_GEOMETRY_LAYER);
              }
            });

    private LayerDefs() {}
  }

  /** Input catalog definitions */
  static class In {

    static final String RIB_CATALOG = "rib";
    static final String ROAD_LAYER = "road-attributes";
    static final String TOPOLOGY_LAYER = "topology-geometry";

    private In() {}
  }

  /** Output catalog definitions */
  static class Out {

    static final String ROAD_GEOMETRY_LAYER = "pedestriansegments";

    private Out() {}
  }

  private Defs() {}
}
