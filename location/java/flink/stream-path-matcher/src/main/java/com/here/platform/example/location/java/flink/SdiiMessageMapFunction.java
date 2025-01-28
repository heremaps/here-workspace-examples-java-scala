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

package com.here.platform.example.location.java.flink;

import com.here.hrn.HRN;
import com.here.platform.data.client.flink.javadsl.FlinkDataClient;
import com.here.platform.data.client.javadsl.Partition;
import com.here.sdii.v3.SdiiMessage.Message;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SdiiMessageMapFunction extends RichMapFunction<Partition, Message> {
  private static final long serialVersionUID = -1L;
  private final Logger log = LoggerFactory.getLogger(SdiiMessageMapFunction.class);
  private FlinkDataClient flinkDataClient;

  private final HRN hrn;

  SdiiMessageMapFunction(final HRN hrn) {
    this.hrn = hrn;
  }

  @Override
  public void open(final Configuration parameters) {
    flinkDataClient = new FlinkDataClient();
  }

  @Override
  public void close() {
    flinkDataClient.terminate();
  }

  @Override
  public Message map(final Partition partition) throws Exception {
    final Message message =
        Message.parseFrom(flinkDataClient.readEngine(hrn).getDataAsBytes(partition));
    log.info("Parsed SDII message with id {}", message.getEnvelope().getTransientVehicleUUID());
    return message;
  }
}
