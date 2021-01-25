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

package com.here.platform.example.location.java.flink;

import com.here.hrn.HRN;
import com.here.platform.data.client.flink.javadsl.FlinkDataClient;
import com.here.platform.data.client.flink.javadsl.FlinkQueryApi;
import com.here.platform.data.client.flink.javadsl.FlinkWriteEngine;
import com.here.platform.data.client.javadsl.NewPartition;
import com.here.platform.data.client.model.PendingPartition;
import com.here.platform.data.client.settings.ConsumerSettings;
import com.here.platform.location.integration.herecommons.geospatial.HereTileLevel;
import com.here.platform.pipeline.InputCatalogDescription;
import com.here.platform.pipeline.PipelineContext;
import com.here.platform.pipeline.ProcessingType;
import com.here.sdii.v3.SdiiMessage;
import com.twitter.chill.protobuf.ProtobufSerializer;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StreamPathMatcherExample {
  private static final Duration CHECKPOINT_INTERVAL = Duration.ofSeconds(30);
  private static final String inputCatalogLayerName = "sample-streaming-layer";
  private static final String outputCatalogLayerName = "out-data";
  private static final HereTileLevel PARTITION_TILE_LEVEL = new HereTileLevel(7);

  public static void main(final String[] args) throws Exception {
    final Logger log = LoggerFactory.getLogger(StreamPathMatcherExample.class);

    final FlinkDataClient dataClient = new FlinkDataClient();
    try {
      final PipelineContext context = new PipelineContext();
      final HRN inputCatalogHRN = context.inputCatalogDescription("sdii-catalog").hrn();
      final InputCatalogDescription optimizedMapCatalog =
          context.inputCatalogDescription("optimized-map-catalog");
      final HRN optimizedMapHRN = optimizedMapCatalog.hrn();
      final HRN outputCatalogHRN = context.getConfig().getOutputCatalog();

      final Optional<ProcessingType> optimizedMapCatalogProcessing =
          optimizedMapCatalog.getProcessing();
      if (!optimizedMapCatalogProcessing.isPresent())
        throw new RuntimeException("optimized-map-catalog version was not present");
      final long optimizedMapCatalogVersion = optimizedMapCatalogProcessing.get().version();

      final FlinkQueryApi queryApi = dataClient.queryApi(inputCatalogHRN);
      final FlinkWriteEngine writeEngine = dataClient.writeEngine(outputCatalogHRN);

      final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
      env.getConfig()
          .registerTypeWithKryoSerializer(SdiiMessage.Message.class, ProtobufSerializer.class);
      env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
      env.enableCheckpointing(CHECKPOINT_INTERVAL.toMillis());

      env.addSource(
              queryApi.subscribe(
                  inputCatalogLayerName,
                  new ConsumerSettings.Builder()
                      .withGroupName("StreamPathMatcherExample-" + UUID.randomUUID())
                      .build()))
          .name("read_sdii_message")
          .map(new SdiiMessageMapFunction(inputCatalogHRN))
          .name("parse_sdii_message")
          .partitionCustom(new TilePartitioner(PARTITION_TILE_LEVEL), Utils::firstPositionEstimate)
          .map(new PathMatcherMapFunction(optimizedMapHRN, optimizedMapCatalogVersion))
          .name("mapmatch_sdii_message")
          .map(
              matched -> {
                log.info("Publishing result for id {}: {}", matched.msgId, matched.status);
                return (PendingPartition)
                    new NewPartition.Builder()
                        .withPartition(UUID.randomUUID().toString())
                        .withLayer(outputCatalogLayerName)
                        .withData(
                            String.format("Result for id %s: %s", matched.msgId, matched.status)
                                .getBytes())
                        .build();
              })
          .name("create_partition_from_mapmatch_result")
          .addSink(writeEngine.publish());

      env.execute("Map match SDII events");
    } finally {
      dataClient.terminate();
    }
  }
}
