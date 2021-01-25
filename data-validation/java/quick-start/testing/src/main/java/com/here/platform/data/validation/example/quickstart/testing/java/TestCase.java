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

package com.here.platform.data.validation.example.quickstart.testing.java;

import com.here.platform.data.processing.driver.Default;
import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.pipeline.logging.LogContext;
import com.here.platform.schema.data.validation.core.testing.v1.Testing.TestResult;
import com.here.platform.schema.data.validation.example.quickstart.input.v1.Schema.LineSegments;
import com.here.platform.schema.data.validation.example.quickstart.testing.v1.Testing.Result;
import com.here.schema.geometry.v2.GeometryOuterClass.LineString;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

// The actual map-reduce implementation that validates the input line segments.
public class TestCase
    implements com.here.platform.data.validation.core.java.testing.testcases.TestCase<
        LineSegments, Result> {

  private static final long serialVersionUID = 2068268447584985397L;

  private TestingConfig config;

  public TestCase(TestingConfig testingConfig) {
    config = testingConfig;
  }

  @Override
  public String id() {
    return "quickstarttestcase";
  }

  @Override
  public Iterable<Pair<Key, LineSegments>> map(
      Pair<Key, LineSegments> context, LogContext logContext) {
    // We simply map the input partition to its corresponding partition in the output layer.
    Key outKey =
        new Key(Default.OutCatalogId().name(), "test-result", context.getKey().partition());

    return Collections.singleton(new Pair<>(outKey, context.getValue()));
  }

  @Override
  public Optional<Result> reduce(Pair<Key, Iterable<LineSegments>> data, LogContext logContext) {

    // The expected octagon has 9 points (to form a closed loop)
    List<LineString> failGeometry =
        StreamSupport.stream(data.getValue().spliterator(), true)
            .flatMap(segment -> segment.getGeometryList().stream())
            .filter(geoList -> geoList.getPointCount() != 9)
            .collect(Collectors.toList());

    if (!failGeometry.isEmpty()) {

      // For non-octagon line segments, set our result to FAIL and make the geometry available for
      // inspection
      // in the output protobuf message

      return java.util.Optional.of(
          Result.newBuilder()
              .setResult(
                  TestResult.newBuilder().setTestid(id()).setValue(TestResult.TestResultType.FAIL))
              .addAllGeometry(failGeometry)
              .build());
    }

    if (config.getWritePassPartitions()) {

      // If we are configured to write PASS partitions to the output, set our result to PASS and
      // make the
      // geometry available for inspection in the output protobuf message
      List<LineString> allGeometry =
          StreamSupport.stream(data.getValue().spliterator(), true)
              .flatMap(segment -> segment.getGeometryList().stream())
              .collect(Collectors.toList());
      return java.util.Optional.of(
          Result.newBuilder()
              .setResult(
                  TestResult.newBuilder().setTestid(id()).setValue(TestResult.TestResultType.PASS))
              .addAllGeometry(allGeometry)
              .build());
    }

    // Otherwise PASS results are not represented in the output catalog
    return java.util.Optional.empty();
  }
}
