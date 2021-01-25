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
package com.here.platform.index.compaction.batch.runner;

import com.here.platform.index.compaction.batch.Driver;
import java.util.concurrent.ExecutionException;
import javax.activation.UnsupportedDataTypeException;

/**
 * Before running this class, you should update values in following files.
 * `parquet-example/src/test/resources/pipeline-config.conf`
 * `parquet-example/src/main/resources/application.conf`
 * `parquet-example/src/test/resources/credentials.properties`
 */
public class ParquetCompactionExampleRunner {

  public static void main(String[] args)
      throws InterruptedException, ExecutionException, ClassNotFoundException,
          InstantiationException, IllegalAccessException, UnsupportedDataTypeException {
    Driver.main(new String[] {"--localSparkMaster"});
  }
}
