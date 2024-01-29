/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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

package com.here.platform.data.processing.example.scala.difftool.processor

import com.here.platform.data.processing.compiler.{InKey, InMeta}

/** A convenient wrapper to store all information necessary to load a concrete partition using the method
  * [[com.here.platform.data.processing.blobstore.Retriever.getPayload(Key, Meta)]]
  *
  * @param key The input tile key
  * @param meta The input tile metadata
  *
  */
case class IntermediateData(key: InKey, meta: InMeta)
