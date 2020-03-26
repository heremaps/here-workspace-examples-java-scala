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

package com.here.platform.data.validation.example.quickstart.comparison.java;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.blobstore.Payload;
import com.here.platform.data.processing.java.blobstore.Retriever;
import com.here.platform.data.processing.java.catalog.partition.Generic;
import com.here.platform.data.processing.java.catalog.partition.HereTile;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import com.here.platform.data.processing.java.catalog.partition.Name;
import com.here.platform.data.processing.java.driver.Default;
import com.here.platform.data.validation.core.java.comparison.ContextHelper;
import com.here.platform.data.validation.core.java.comparison.Triple;
import com.here.platform.data.validation.core.java.comparison.metadiff.grouped.DiffByPartitions;
import com.here.platform.schema.data.validation.example.quickstart.comparison.v1.Schema.ComparedLineSegments;
import com.here.platform.schema.data.validation.example.quickstart.input.v1.Schema;
import java.util.ArrayList;
import java.util.Optional;

class QuickstartComparisonByPartition extends DiffByPartitions {
  private static final long serialVersionUID = -6833701010307764313L;

  private ContextHelper cth;

  public QuickstartComparisonByPartition(final ContextHelper helper) {
    this.cth = helper;
  }

  @Override
  public java.lang.Iterable<Pair<Key, Optional<Payload>>> handleDiff(
      final String partition,
      final java.lang.Iterable<Triple<String, Optional<Meta>, Optional<Meta>>> layered) {
    final ArrayList<Pair<Key, Optional<Payload>>> results = new ArrayList<>();
    for (Triple<String, Optional<Meta>, Optional<Meta>> t : layered) {
      String layer = t.getLeft().replace("'", "");
      if (layer.equals(LayerDefs.hereTiledInputLayer)) {
        final HereTile hereTile = new HereTile(Long.parseLong(partition));

        Key out = getKey(hereTile, Default.OutCatalogId(), LayerDefs.hereTiledOutputLayer);

        final Payload candidate =
            getPayload(
                hereTile, cth.candidateRetriever(), ContextHelper.CANDIDATECATID(), t.getMiddle());
        final Payload reference =
            getPayload(
                hereTile, cth.referenceRetriever(), ContextHelper.REFERENCECATID(), t.getRight());

        try {
          final ComparedLineSegments.Builder geometry =
              ComparedLineSegments.newBuilder()
                  .addCandidateGeometry(
                      Schema.LineSegments.parseFrom(candidate.content()).getGeometry(0))
                  .addReferenceGeometry(
                      Schema.LineSegments.parseFrom(reference.content()).getGeometry(0));
          final Pair<Key, Optional<Payload>> pair =
              new Pair<>(out, Optional.of(new Payload(geometry.build().toByteArray())));
          results.add(pair);

        } catch (InvalidProtocolBufferException e) {
          e.printStackTrace();
        }

      } else {
        String genericName = layer + "-" + partition;
        Key out =
            getKey(new Generic(genericName), Default.OutCatalogId(), LayerDefs.genericOutLayer);
        results.add(new Pair<>(out, Optional.of(new Payload("\"checksum differs\"".getBytes()))));
      }
    }
    return results;
  }

  private Payload getPayload(
      final HereTile hereTile, final Retriever rc, final String catid, final Optional<Meta> meta) {

    return rc.getPayload(getKey(hereTile, catid, LayerDefs.hereTiledInputLayer), meta.get());
  }

  private Key getKey(final Name name, final String catalog, final String layer) {
    return new Key(catalog, layer, name);
  }
}
