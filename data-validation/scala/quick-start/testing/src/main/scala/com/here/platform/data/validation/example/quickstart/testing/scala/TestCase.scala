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

package com.here.platform.data.validation.example.quickstart.testing.scala

import com.here.platform.data.processing.compiler.{InKey, OutKey}
import com.here.platform.data.processing.driver.Default
import com.here.platform.data.validation.core.testing.testcases.{TestCase => BaseTestCase}
import com.here.platform.data.validation.example.quickstart.common.{LayerDefs => CommonLayerDefs}
import com.here.platform.schema.data.validation.core.testing.v1.testing.TestResult
import com.here.platform.schema.data.validation.core.testing.v1.testing.TestResult.TestResultType
import com.here.platform.schema.data.validation.example.quickstart.testing.v1.testing.Result
import com.here.platform.schema.data.validation.example.quickstart.input.v1.schema.LineSegments
import com.here.schema.geometry.v2.geometry.LineString

/** This map-reduce test case implementation
  * verifies that the feature geometries are octagons (have eight elements).
  */
class TestCase(val testingConfig: TestingConfig) extends BaseTestCase[LineSegments, Result] {
  val id: String = "quickstarttestcase"

  // The output partition is the same as the input, except that it is in the output catalog and layer.
  def map(context: (InKey, LineSegments)): Iterable[(OutKey, LineSegments)] =
    Iterable(
      context.key
        .copy(catalog = Default.OutCatalogId,
              layer = CommonLayerDefs.Testing.Out.testResultLayerName) -> context.value)

  /** Runs testcase logic over the LineSegments for the output partition.
    *
    * @param data       The output key and LineSegments for the given output partition
    * @return The byte array that contains the testcase results for the output partition
    */
  def reduce(data: (OutKey, Iterable[LineSegments])): Option[Result] = {
    val nonOctagonGeometry: Seq[LineString] =
      data.value
        .flatMap(segment => segment.geometry)
        .filter(geom => geom.point.size != 9)
        .toSeq

    if (nonOctagonGeometry.isEmpty) {
      if (testingConfig.writePassPartitions) {
        // If we are configured to write PASS partitions to the output, set our result to PASS and make the
        // geometry available for inspection in the output protobuf message
        val allGeometry: Seq[LineString] =
          data.value
            .flatMap(segment => segment.geometry)
            .toSeq
        Some(
          Result(TestResult(id, TestResultType.PASS), allGeometry)
        )
        // Otherwise PASS results are not represented in the output catalog
      } else None
    } else {
      // For non-octagon line segments, set our result to FAIL and make the geometry available for inspection
      // in the output protobuf message
      Some(
        Result(TestResult(id, TestResultType.FAIL), nonOctagonGeometry)
      )
    }
  }
}
