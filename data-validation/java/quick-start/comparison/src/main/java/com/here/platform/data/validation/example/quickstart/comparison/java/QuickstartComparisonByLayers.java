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

import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Payload;
import com.here.platform.data.processing.java.catalog.partition.Generic;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.driver.Default;
import com.here.platform.data.validation.core.java.comparison.Triple;
import com.here.platform.data.validation.core.java.comparison.metadiff.grouped.DiffByLayers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

class QuickstartComparisonByLayers extends DiffByLayers {
  private static final long serialVersionUID = -6833701010307764313L;

  @Override
  public Iterable<Pair<Key, Optional<Payload>>> handleDiff(
      final String layer,
      final Iterable<Triple<String, Optional<Meta>, Optional<Meta>>> partitioned) {
    final String name = "diff-" + layer.replace("'", "");
    String start = "{\"partitions\":[{";
    String end = "}]}";
    ArrayList<String> l = new ArrayList<>();

    for (Triple<String, Optional<Meta>, Optional<Meta>> t : partitioned) {
      l.add(t.getLeft());
    }
    Key key = new Key(Default.OutCatalogId(), LayerDefs.genericOutLayer, new Generic(name));
    String json = start + String.join(", ", l) + end;
    Optional<Payload> o = Optional.of(new Payload(json.getBytes()));
    Pair<Key, Optional<Payload>> p = new Pair<>(key, o);
    return Collections.singletonList(p);
  }
}
