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
package com.here.platform.index.compaction.batch;

import com.google.protobuf.InvalidProtocolBufferException;
import com.here.platform.index.compaction.core.CompactionUDF;
import com.here.sdii.v3.SdiiMessage;
import com.here.sdii.v3.SdiiMessageList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtobufCompactionExample implements CompactionUDF {

  private static final long serialVersionUID = -4852933126361068870L;
  private static final Logger LOG = LoggerFactory.getLogger(ProtobufCompactionExample.class);

  @Override
  public byte[] merge(Map<String, Object> keys, Iterator<byte[]> files) {
    List<SdiiMessage.Message> sdiiMessages = new ArrayList<>();
    while (files.hasNext()) {
      byte[] file = files.next();
      try {
        SdiiMessageList.MessageList sdiiMessageList = SdiiMessageList.MessageList.parseFrom(file);
        sdiiMessages.addAll(sdiiMessageList.getMessageList());
      } catch (InvalidProtocolBufferException e) {
        LOG.error("Error merging sdii messages (key={})", keys.values(), e);
        return null;
      }
    }
    return SdiiMessageList.MessageList.newBuilder()
        .addAllMessage(sdiiMessages)
        .build()
        .toByteArray();
  }
}
