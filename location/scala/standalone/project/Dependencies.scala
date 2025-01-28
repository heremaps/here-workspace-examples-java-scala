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

import sbt._
import com.here.bom.Bom

case class Dependencies(platformBom: Bom) {
  val dependencies: Seq[ModuleID] = Seq(
    "com.here.platform.location" %% "location-compilation-here-map-content" % platformBom,
    "com.here.platform.location" %% "location-core" % platformBom,
    "com.here.platform.location" %% "location-data-loader-core" % platformBom,
    "com.here.platform.location" %% "location-data-loader-standalone" % platformBom,
    "com.here.platform.location" %% "location-inmemory" % platformBom,
    "com.here.platform.location" %% "location-integration-optimized-map" % platformBom,
    "com.here.platform.location" %% "location-integration-optimized-map-dcl2" % platformBom,
    "com.here.platform.location" %% "location-tpeg2" % platformBom,
    "com.here.platform.location" %% "location-referencing" % platformBom,
    "com.here.platform.location" %% "location-io" % platformBom,
    "com.here.schema" %% "geometry_v2_scala" % platformBom excludeAll ("com.here.schema" % "geometry_v2_proto"),
    "com.here.schema.rib" %% "advanced-navigation-attributes_v2_scala" % platformBom excludeAll ("com.here.schema.rib" % "advanced-navigation-attributes_v2_proto"),
    "com.here.schema.rib" %% "common_v2_scala" % platformBom excludeAll ("com.here.schema.rib" % "common_v2_proto"),
    "com.here.traffic.realtime" %% "traffic_v2_scala" % platformBom,
    "com.github.tototoshi" %% "scala-csv" % platformBom,
    "stax" % "stax" % "1.2.0"
  )
  val allDependencies: Seq[ModuleID] = platformBom.bomDependencies
}

trait DependenciesTrait {
  def dependencies: Seq[ModuleID]
}
