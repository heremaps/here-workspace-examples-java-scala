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

package com.here.platform.example.location.java.flink;

import com.here.hrn.HRN;
import com.here.platform.data.client.base.javadsl.BaseClient;
import com.here.platform.data.client.base.javadsl.BaseClientJava;
import com.here.platform.location.integration.optimizedmap.OptimizedMapLayers;
import com.here.platform.location.integration.optimizedmap.dcl2.javadsl.OptimizedMapCatalog;
import com.here.platform.pipeline.InputCatalogDescription;
import com.here.platform.pipeline.PipelineContext;
import com.here.platform.pipeline.ProcessingType;
import java.util.Optional;

public enum OptimizedMapLayersSingleton {
  INSTANCE;

  private final OptimizedMapLayers optimizedMapLayers;

  OptimizedMapLayersSingleton() {
    final PipelineContext context = new PipelineContext();
    final InputCatalogDescription optimizedMapCatalog =
        context.inputCatalogDescription("optimized-map-catalog");
    final HRN optimizedMapHRN = optimizedMapCatalog.hrn();

    final Optional<ProcessingType> optimizedMapCatalogProcessing =
        optimizedMapCatalog.getProcessing();
    if (!optimizedMapCatalogProcessing.isPresent())
      throw new RuntimeException("optimized-map-catalog version was not present");
    final long optimizedMapCatalogVersion = optimizedMapCatalogProcessing.get().version();

    BaseClient baseClient = BaseClientJava.instance();
    optimizedMapLayers =
        OptimizedMapCatalog.from(optimizedMapHRN)
            .usingBaseClient(baseClient)
            // Reserving up to 4GiB of RAM in the Optimized Map tiles cache
            .withMaxBlobCacheSizeBytes(4L << 30)
            .newInstance()
            .version(optimizedMapCatalogVersion);
  }

  public OptimizedMapLayers getOptimizedMapLayers() {
    return optimizedMapLayers;
  }
}
