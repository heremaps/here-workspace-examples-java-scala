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

package com.here.platform.data.processing.example.java.geometry.lifter;

import com.here.platform.data.processing.java.catalog.partition.Key;
import com.here.platform.data.processing.java.catalog.partition.Meta;
import java.io.Serializable;

/**
 * Convenient wrapper to store all information that allows to load the actual partition data i.e.
 * {@link com.here.platform.data.processing.java.blobstore.Retriever#getPayload(Key, Meta)}
 */
public class IntermediateData implements Serializable {

  private Key key;
  private Meta meta;

  public IntermediateData(Key key, Meta meta) {
    this.key = key;
    this.meta = meta;
  }

  public Key getKey() {
    return key;
  }

  public Meta getMeta() {
    return meta;
  }
}
