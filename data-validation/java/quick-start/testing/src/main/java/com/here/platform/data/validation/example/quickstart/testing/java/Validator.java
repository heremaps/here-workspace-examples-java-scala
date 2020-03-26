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

package com.here.platform.data.validation.example.quickstart.testing.java;

import com.here.platform.data.validation.core.java.testing.context.MapGroupFeatureLoader;
import com.here.platform.data.validation.core.java.testing.testcases.families.TestFamily;
import com.here.platform.data.validation.core.java.testing.validators.MapGroupValidator;
import com.here.platform.schema.data.validation.example.quickstart.input.v1.Schema.LineSegments;
import com.here.platform.schema.data.validation.example.quickstart.testing.v1.Testing.Result;

// Validates line segments from the input
public class Validator extends MapGroupValidator<LineSegments, Result> {

  private static final long serialVersionUID = 4805127769342666921L;

  public Validator(
      TestFamily<LineSegments, Result> mapReduceFamily,
      MapGroupFeatureLoader<LineSegments> featureLoader) {
    super(mapReduceFamily, featureLoader);
  }
}
