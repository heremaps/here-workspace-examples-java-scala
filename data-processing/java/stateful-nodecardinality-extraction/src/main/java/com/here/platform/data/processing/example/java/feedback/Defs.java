/*
 * Copyright (C) 2017-2025 HERE Europe B.V.
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

package com.here.platform.data.processing.example.java.feedback;

import com.google.common.collect.ImmutableMap;
import com.here.platform.data.processing.java.driver.Default;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

class Defs {

  /** Input and output layers configuration for the stateful compiler */
  static class LayerDefs {

    static final Map<String, Set<String>> inLayers =
        ImmutableMap.of(
            In.RIB_CATALOG,
            Collections.singleton(In.TOPOLOGY_LAYER),
            // layer ID that refers to the current base version of the output
            // catalog (output produced from the previous run)
            Default.FeedbackCatalogId(),
            Collections.singleton(In.NODECARDINALITY_LAYER));

    static final Set<String> outLayers = Collections.singleton(Out.NODECARDINALITY_LAYER);

    private LayerDefs() {}
  }

  /** Input catalog definitions */
  static class In {
    private In() {}

    // The input catalog ID must match the corresponding entry in the input-catalogs section of
    // pipeline-config.conf
    static final String RIB_CATALOG = "rib";

    static final String TOPOLOGY_LAYER = "topology-geometry";
    static final String NODECARDINALITY_LAYER = "nodecardinality-count";
  }

  /** Output catalog definitions */
  static class Out {
    private Out() {}

    static final String NODECARDINALITY_LAYER = "nodecardinality-count";
  }

  private Defs() {}
}
