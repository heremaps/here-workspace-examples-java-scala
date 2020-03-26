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

package com.here.platform.data.validation.example.quickstart.assessment.java;

import com.here.platform.data.processing.java.Pair;
import com.here.platform.data.processing.java.catalog.partition.Generic;
import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Name;
import com.here.platform.pipeline.logging.LogContext;
import com.here.platform.schema.data.validation.core.assessment.v1.Assessment.TestAssessment;
import com.here.platform.schema.data.validation.core.metrics.v1.Metrics.TestMetricSeverity.SeverityType;
import com.here.platform.schema.data.validation.example.quickstart.assessment.v1.AssessmentOuterClass.Assessment;
import com.here.platform.schema.data.validation.example.quickstart.metrics.v1.Metrics.MetricSample;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

// The actual MapReduce implementation that assesses the error metrics
public class Criteria
    extends com.here.platform.data.validation.core.java.assessment.criteria.QualityCriteria<
        Data, Assessment> {

  private static final long serialVersionUID = -3398665268852475717L;

  private AssessmentConfig config;

  public Criteria(AssessmentConfig assessmentConfig) {
    config = assessmentConfig;
  }

  @Override
  public String id() {
    return "quickstartcriteria";
  }

  @Override
  public java.util.Optional<Assessment> reduce(
      Pair<Key, Iterable<Data>> data, LogContext logContext) {

    // Count all tileIDs from all CRITICAL errors
    int criticalCount =
        StreamSupport.stream(data.getValue().spliterator(), false)
            .flatMap(
                dataInstance -> dataInstance.getMetric().map(Stream::of).orElseGet(Stream::empty))
            .flatMap(metric -> metric.getMetricsList().stream())
            .filter(sample -> sample.getSeverity().getValue() == SeverityType.CRITICAL)
            .map(MetricSample::getTileIdsCount)
            .mapToInt(i -> i)
            .sum();

    // Count all tiles from input candidate catalog
    int allCount =
        StreamSupport.stream(data.getValue().spliterator(), false)
            .map(Data::getTileCount)
            .mapToInt(i -> i)
            .sum();

    double criticalPercentage = (double) criticalCount / allCount;

    TestAssessment.TestAssessmentResultType resultType =
        criticalPercentage > config.getCriticalThreshold()
            ? TestAssessment.TestAssessmentResultType.FAIL
            : TestAssessment.TestAssessmentResultType.PASS;

    TestAssessment status =
        TestAssessment.newBuilder().setAssessmentid(id()).setValue(resultType).build();

    return java.util.Optional.of(
        Assessment.newBuilder()
            .setCriticalThreshold(config.getCriticalThreshold())
            .setCriticalPercent(criticalPercentage)
            .setNumCriticalErrors(criticalCount)
            .setNumCandidateTiles(allCount)
            .setStatus(status)
            .build());
  }

  @Override
  public String assessmentResultLayer() {
    return "assessment";
  }

  @Override
  public Name assessmentResultPartition() {
    return new Generic("ASSESSMENT");
  }
}
