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

package com.here.platform.data.archive.example.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.sensoris.messages.data.DataMessage;

public class SensorisMessageParquetWriterBuilder
    extends ParquetWriter.Builder<DataMessage, SensorisMessageParquetWriterBuilder> {
  protected SensorisMessageParquetWriterBuilder(Path path) {
    super(path);
  }

  @Override
  protected SensorisMessageParquetWriterBuilder self() {
    return this;
  }

  @Override
  protected WriteSupport<DataMessage> getWriteSupport(Configuration configuration) {
    return new CustomProtoWriteSupport<>(DataMessage.class);
  }
}
