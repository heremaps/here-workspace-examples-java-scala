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

package com.here.platform.data.archive.example;

import com.here.sdii.v3.SdiiMessage.Message;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;

public class SdiiMessageParquetWriterBuilder
    extends ParquetWriter.Builder<Message, SdiiMessageParquetWriterBuilder> {
  protected SdiiMessageParquetWriterBuilder(Path path) {
    super(path);
  }

  @Override
  protected SdiiMessageParquetWriterBuilder self() {
    return this;
  }

  @Override
  protected WriteSupport<Message> getWriteSupport(Configuration configuration) {
    return new CustomProtoWriteSupport<>(Message.class);
  }
}
