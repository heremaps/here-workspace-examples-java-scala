/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
    "com.here.platform.data.client" %% "local-support" % platformBom excludeAll (
      ExclusionRule(organization = "com.fasterxml.jackson.core"),
      ExclusionRule(organization = "com.here.platform.data.client", name = "client-core_2.12")
    ),
    "com.here.hrn" %% "hrn" % platformBom,
    "com.here.platform.data.client" %% "client-core" % platformBom,
    "com.here.platform.data.client" %% "data-client" % platformBom,
    "com.here.platform.data.client" %% "data-engine" % platformBom,
    "com.here.platform.data.client" %% "spark-support" % platformBom,
    "com.here.platform.location" %% "location-core" % platformBom,
    "com.here.platform.location" %% "location-inmemory" % platformBom,
    "com.here.platform.location" %% "location-integration-here-commons" % platformBom,
    "com.here.platform.location" %% "location-integration-optimized-map-dcl2" % platformBom,
    "com.here.platform.location" %% "location-io" % platformBom,
    "com.here.platform.location" %% "location-spark" % platformBom,
    "com.here.platform.pipeline" %% "pipeline-interface" % platformBom,
    "com.typesafe.akka" %% "akka-actor" % platformBom,
    "org.apache.spark" %% "spark-core" % platformBom,
    "org.apache.spark" %% "spark-sql" % platformBom,
    "org.slf4j" % "slf4j-api" % platformBom
  )
}

trait DependenciesTrait {
  def dependencies: Seq[ModuleID]
}
