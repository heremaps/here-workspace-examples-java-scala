import com.here.bom.Bom

ThisBuild / organization := "com.here.platform.example.location"
ThisBuild / version := "0.0.907"
ThisBuild / scalaVersion := "2.12.18"

val organizationSettings: Seq[Setting[_]] = Seq(
  projectInfo := ModuleInfo(
    nameFormal = "scala-standalone_2.12",
    description = "Location Library Examples - Scala - Standalone",
    homepage = Some(url("http://here.com")),
    startYear = Some(2019),
    licenses = Vector(("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))),
    organizationName = "HERE Europe B.V",
    organizationHomepage = Some(url("http://here.com")),
    scmInfo = Some(
      ScmInfo(
        connection = "scm:git:https://github.com/heremaps/here-workspace-examples-java-scala.git",
        devConnection = "scm:git:git@github.com:heremaps/here-workspace-examples-java-scala.git",
        browseUrl = url("https://github.com/heremaps/here-workspace-examples-java-scala")
      )
    ),
    developers = Vector()
  )
)

val sdkBomVersion = "2.66.7"

assembly / assemblyJarName := f"${name.value}-${version.value}-platform.jar"
assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) =>
    (xs map {
      _.toLowerCase
    }) match {
      case ps @ (x :: xs)
          if ps.last.toLowerCase.endsWith(".sf") || ps.last.toLowerCase.endsWith(".dsa") || ps.last.toLowerCase
            .endsWith(".rsa") =>
        MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

lazy val deps =
  Bom.read("com.here.platform" %% "sdk-batch-bom" % sdkBomVersion)(bom => Dependencies(bom))

lazy val root = (project in file("."))
  .settings(deps)
  .settings(organizationSettings)
  .settings(
    libraryDependencies ++= deps.key.value.dependencies
  )
  .settings(
    dependencyOverrides ++= deps.key.value.allDependencies
  )

resolvers += "HERE_PLATFORM_ARTIFACT" at "here+artifact-service://artifact-service"
