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

package com.here.platform.data.validation.example.quickstart.assessment.java;

import com.here.platform.data.validation.core.java.assessment.assessors.DirectAssessor;
import com.here.platform.data.validation.core.java.assessment.context.DirectFeatureLoader;
import com.here.platform.schema.data.validation.example.quickstart.assessment.v1.AssessmentOuterClass.Assessment;

// Assesses error metrics
public class Assessor extends DirectAssessor<Data, Assessment> {

  private static final long serialVersionUID = -8268149949526254624L;

  public Assessor(
      com.here.platform.data.validation.core.java.assessment.criteria.families.CriteriaFamily<
              Data, Assessment>
          mapReduceFamily,
      DirectFeatureLoader<Data> featureLoader) {
    super(mapReduceFamily, featureLoader);
  }
}
