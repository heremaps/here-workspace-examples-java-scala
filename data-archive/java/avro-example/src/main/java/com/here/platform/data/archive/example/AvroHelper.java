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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.file.SeekableInput;
import org.apache.avro.protobuf.ProtobufData;
import org.apache.avro.protobuf.ProtobufDatumReader;
import org.apache.avro.protobuf.ProtobufDatumWriter;

public class AvroHelper {

  private AvroHelper() {
    throw new UnsupportedOperationException();
  }

  public static <V> byte[] aggregateProtobufMessagesAsAvro(Iterator<V> iterator, Class<V> cls)
      throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    asAvro(iterator, cls, os);
    os.close();
    return os.toByteArray();
  }

  public static <V> void asAvro(Iterator<V> iterator, Class<V> cls, OutputStream os)
      throws Exception {
    ProtobufDatumWriter<V> writer = new ProtobufDatumWriter<>(cls);
    try (DataFileWriter<V> dataFileWriter = new DataFileWriter<>(writer)) {
      Schema schema = ProtobufData.get().getSchema(cls);
      dataFileWriter.create(schema, os);
      while (iterator.hasNext()) {
        dataFileWriter.append(iterator.next());
      }
    }
  }

  public static <V> byte[] mergeAvroFiles(Iterator<byte[]> list, Class<V> cls) throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    mergeStreamBytes(list, cls, os);
    os.close();
    return os.toByteArray();
  }

  public static <V> void mergeStreamBytes(Iterator<byte[]> iterator, Class<V> cls, OutputStream os)
      throws Exception {
    ProtobufDatumWriter<V> writer = new ProtobufDatumWriter<>(cls);
    ProtobufDatumReader<V> reader = new ProtobufDatumReader<>(cls);
    try (DataFileWriter<V> dataFileWriter = new DataFileWriter<>(writer)) {
      Schema schema = ProtobufData.get().getSchema(cls);
      dataFileWriter.create(schema, os);
      while (iterator.hasNext()) {
        try (SeekableInput input = new SeekableByteArrayInput(iterator.next());
            DataFileReader<V> dataFileReader = new DataFileReader<>(input, reader)) {
          while (dataFileReader.hasNext()) {
            dataFileWriter.append(dataFileReader.next());
          }
        }
      }
    }
  }

  public static <V> List<V> fromFile(File file, Class<V> cls) throws IOException {
    ProtobufDatumReader<V> reader = new ProtobufDatumReader<>(cls);
    DataFileReader<V> dataFileReader = new DataFileReader<>(file, reader);
    List<V> list = new LinkedList<>();
    while (dataFileReader.hasNext()) {
      list.add(dataFileReader.next());
    }
    dataFileReader.close();
    return list;
  }
}
