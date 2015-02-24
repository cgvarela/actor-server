import sbt._

object Resolvers {
  val mavenOrg = {
    val r = new org.apache.ivy.plugins.resolver.IBiblioResolver
    r.setM2compatible(true)
    r.setName("maven repo")
    r.setRoot("http://repo1.maven.org/maven2/")
    r.setCheckconsistency(false)
    new RawRepository(r)
  }

  lazy val seq = Seq(
    mavenOrg,
    "typesafe repo"       at "http://repo.typesafe.com/typesafe/releases",
    "sonatype snapshots"  at "https://oss.sonatype.org/content/repositories/snapshots/",
    "sonatype releases"   at "https://oss.sonatype.org/content/repositories/releases/",
    "spray repo"          at "http://repo.spray.io",
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
    "Websudos releases" at "http://maven.websudos.co.uk/ext-release-local",
    "secret snapshots repository" at "http://repos.81port.com/nexus/content/repositories/snapshots",
    "secret releases repository" at "http://repos.81port.com/nexus/content/repositories/releases",
    "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven",
    "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"
  )
}
