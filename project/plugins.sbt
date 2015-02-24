resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/",
  Resolver.url("secret repository", url("http://repos.81port.com/nexus/content/repositories/snapshots"))(Resolver.ivyStylePatterns),
  "Flyway" at "http://flywaydb.org/repo"
)

addSbtPlugin("com.typesafe.akka" % "akka-sbt-plugin" % "2.2.3")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("com.github.sbt" % "sbt-scalabuff" % "1.3.8-SNAPSHOT-SECRET")

addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.3.3")

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "3.1")
