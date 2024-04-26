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

import sbt._
import com.here.bom.Bom

case class Dependencies(platformBom: Bom) {
  val dependencies: Seq[ModuleID] = Seq(
    "com.here.schema.rib" %% "road-attributes_v2_scala" % platformBom,
    "com.here.schema.rib" %% "topology-geometry_v2_scala" % platformBom,
    "com.here.platform.data.processing" %% "pipeline-runner" % platformBom,
    "com.here.platform.data.processing" %% "batch-catalog-dataservice" % platformBom,
    "com.here.platform.data.client" %% "local-support" % platformBom excludeAll (ExclusionRule(
      organization = "com.fasterxml.jackson.core")),
    "commons-io" % "commons-io" % platformBom,
    "org.apache.spark" %% "spark-core" % platformBom,
//    output data schema
    "com.here.platform.schema.data.processing.example.scala.pedestrian.protobuf" %% "model_v2_scala" % "0.0.879"
  )
}

trait DependenciesTrait {
  def dependencies: Seq[ModuleID]
}
