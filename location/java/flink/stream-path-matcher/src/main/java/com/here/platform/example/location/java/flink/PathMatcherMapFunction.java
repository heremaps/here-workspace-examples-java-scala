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

package com.here.platform.example.location.java.flink;

import com.here.hrn.HRN;
import com.here.platform.location.core.geospatial.javadsl.GeoCoordinateAdapter;
import com.here.platform.location.core.mapmatching.MatchResult;
import com.here.platform.location.core.mapmatching.NoTransition;
import com.here.platform.location.core.mapmatching.javadsl.MatchResults;
import com.here.platform.location.core.mapmatching.javadsl.PathMatcher;
import com.here.platform.location.dataloader.core.Catalog;
import com.here.platform.location.dataloader.core.caching.CacheManager;
import com.here.platform.location.dataloader.flink.FlinkCatalogFactory;
import com.here.platform.location.inmemory.graph.Vertex;
import com.here.platform.location.integration.optimizedmap.mapmatching.javadsl.PathMatchers;
import com.here.sdii.v3.SdiiCommon.PositionEstimate;
import com.here.sdii.v3.SdiiMessage.Message;
import java.util.List;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

public class PathMatcherMapFunction extends RichMapFunction<Message, MatchedTrip> {
  private static final long serialVersionUID = -1L;
  private HRN optimizedMapHRN;
  private long optimizedMapCatalogVersion;
  private FlinkCatalogFactory catalogFactory;
  private PathMatcher<PositionEstimate, Vertex, NoTransition> pathMatcher;

  PathMatcherMapFunction(final HRN optimizedMapHRN, final long optimizedMapCatalogVersion) {
    this.optimizedMapHRN = optimizedMapHRN;
    this.optimizedMapCatalogVersion = optimizedMapCatalogVersion;
  }

  private GeoCoordinateAdapter<PositionEstimate> positionEstimateAdapter =
      new PositionEstimateGeoCoordinateAdapter();

  @Override
  public void open(final Configuration parameters) throws Exception {
    super.open(parameters);
    catalogFactory = new FlinkCatalogFactory();
    catalogFactory.create(optimizedMapHRN, optimizedMapCatalogVersion);
    final Catalog optimizedMap = catalogFactory.create(optimizedMapHRN, optimizedMapCatalogVersion);
    // 600 is approximately the amount of InMemory tiles that fit in one worker unit
    final CacheManager cacheManager = CacheManager.withLruCache(0L, 600L);
    pathMatcher = PathMatchers.carPathMatcher(optimizedMap, cacheManager, positionEstimateAdapter);
  }

  @Override
  public void close() throws Exception {
    catalogFactory.terminate();
    super.close();
  }

  @Override
  public MatchedTrip map(final Message msg) throws Exception {
    String status;
    try {
      final List<MatchResult<Vertex>> result = matchTrip(msg);
      final long matchedCount = result.stream().filter(m -> !MatchResults.isUnknown(m)).count();
      status = "matched " + matchedCount + " points out of " + result.size();
    } catch (final Exception e) {
      status = "failed to match " + e.getMessage();
    }
    return new MatchedTrip(msg.getEnvelope().getTransientVehicleUUID(), status);
  }

  private List<MatchResult<Vertex>> matchTrip(final Message msg) {
    final List<PositionEstimate> trip = msg.getPath().getPositionEstimateList();
    return pathMatcher.matchPath(trip).results();
  }
}
