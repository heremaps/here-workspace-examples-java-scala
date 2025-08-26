resolvers += Resolver.url("MAVEN_CENTRAL", url("https://repo.maven.apache.org/maven2"))(
  Patterns("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"))
addSbtPlugin("com.here.platform" % "sbt-bom" % "1.0.25")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")
addSbtPlugin("com.here.platform.artifact" % "sbt-resolver" % "2.0.35")
