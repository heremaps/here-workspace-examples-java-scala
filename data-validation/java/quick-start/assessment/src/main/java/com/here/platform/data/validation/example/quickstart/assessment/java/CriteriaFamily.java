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

import com.here.platform.data.validation.core.java.assessment.criteria.families.SingleCriteria;
import com.here.platform.schema.data.validation.example.quickstart.assessment.v1.AssessmentOuterClass.Assessment;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// Single map-reduce family to assess the error metrics
public class CriteriaFamily extends SingleCriteria<Data, Assessment> {

  private static final long serialVersionUID = 3130811564653261909L;

  public CriteriaFamily(Criteria criteria) {
    super(criteria);
  }

  @Override
  public String id() {
    return "quickstartcriteriafamily";
  }

  @Override
  public byte[] toByteArray(Assessment Assessment) {
    return Assessment.toByteArray();
  }

  @Override
  public Set<String> outLayers() {
    // TODO: Declare catalog and layer IDs in their own object, as they are expected to be in sync
    return new HashSet<>(Collections.singletonList("assessment"));
  }
}
