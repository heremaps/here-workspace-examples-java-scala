import com.here.bom.Bom

ThisBuild / organization := "com.here.platform.data.processing.example.scala"
ThisBuild / version := "0.0.865"
ThisBuild / scalaVersion := "2.12.18"
ThisBuild / evictionErrorLevel := sbt.util.Level.Warn
val organizationSettings: Seq[Setting[_]] = Seq(
  projectInfo := ModuleInfo(
    nameFormal = "heremapcontent-difftool",
    description = "HERE Map Content Differences example for Scala",
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

val sdkBomVersion = "2.59.2"

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

assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("com.google.protobuf.**" -> "shaded.com.google.protobuf.@1").inAll
)

lazy val deps =
  Bom.read("com.here.platform" %% "sdk-batch-bom" % sdkBomVersion)(bom => Dependencies(bom))

lazy val root = (project in file("."))
  .settings(deps)
  .settings(organizationSettings)
  .settings(
    libraryDependencies ++= deps.key.value.dependencies
  )

resolvers += "HERE_PLATFORM_ARTIFACT" at "here+artifact-service://artifact-service"
