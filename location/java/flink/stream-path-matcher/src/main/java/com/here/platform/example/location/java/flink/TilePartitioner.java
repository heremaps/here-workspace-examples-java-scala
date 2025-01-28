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

import com.here.platform.location.integration.herecommons.geospatial.HereTileLevel;
import com.here.platform.location.integration.herecommons.geospatial.javadsl.HereTileResolver;
import com.here.sdii.v3.SdiiCommon;
import org.apache.flink.api.common.functions.Partitioner;

@SuppressWarnings("serial")
public class TilePartitioner implements Partitioner<SdiiCommon.PositionEstimate> {

  private static final PositionEstimateGeoCoordinateAdapter adapter =
      new PositionEstimateGeoCoordinateAdapter();

  private final HereTileResolver resolver;

  TilePartitioner(final HereTileLevel level) {
    resolver = new HereTileResolver(level);
  }

  @Override
  public int partition(final SdiiCommon.PositionEstimate geoCoordinate, final int numPartitions) {
    return Math.toIntExact(resolver.fromCoordinate(geoCoordinate, adapter) % numPartitions);
  }
}
