import scala.util.Try

name := "scorex-core"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.10",
  resolvers += Resolver.sonatypeRepo("public"),
  wartremoverErrors ++= Seq(
    Wart.Recursion,
    Wart.TraversableOps,
    Wart.Null,
    Wart.Product,
    Wart.PublicInference,
    Wart.FinalVal,
    Wart.IsInstanceOf,
    Wart.JavaConversions,
    Wart.JavaSerializable,
    Wart.Serializable,
    Wart.OptionPartial),
  organization := "org.scorexfoundation",
  licenses := Seq("CC0" -> url("https://creativecommons.org/publicdomain/zero/1.0/legalcode")),
  homepage := Some(url("https://github.com/ScorexFoundation/Scorex")),
  pomExtra :=
      <developers>
        <developer>
          <id>kushti</id>
          <name>Alexander Chepurnoy</name>
          <url>http://chepurnoy.org/</url>
        </developer>
        <developer>
          <id>catena2w</id>
          <name>catena</name>
          <url>https://github.com/catena2w</url>
        </developer>
      </developers>,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  publishTo := sonatypePublishToBundle.value,
  fork := true // otherwise, "java.net.SocketException: maximum number of DatagramSockets reached"
)

enablePlugins(GitVersioning)

version in ThisBuild := {
  if (git.gitCurrentTags.value.nonEmpty) {
    git.gitDescribedVersion.value.get
  } else {
    if (git.gitHeadCommit.value.contains(git.gitCurrentBranch.value)) {
      // see https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
      if (Try(sys.env("TRAVIS")).getOrElse("false") == "true") {
        // pull request number, "false" if not a pull request
        if (Try(sys.env("TRAVIS_PULL_REQUEST")).getOrElse("false") != "false") {
          // build is triggered by a pull request
          val prBranchName = Try(sys.env("TRAVIS_PULL_REQUEST_BRANCH")).get
          val prHeadCommitSha = Try(sys.env("TRAVIS_PULL_REQUEST_SHA")).get
          prBranchName + "-" + prHeadCommitSha.take(8) + "-SNAPSHOT"
        } else {
          // build is triggered by a push
          val branchName = Try(sys.env("TRAVIS_BRANCH")).get
          branchName + "-" + git.gitHeadCommit.value.get.take(8) + "-SNAPSHOT"
        }
      } else {
        git.gitHeadCommit.value.get.take(8) + "-SNAPSHOT"
      }
    } else {
      git.gitCurrentBranch.value + "-" + git.gitHeadCommit.value.get.take(8) + "-SNAPSHOT"
    }
  }
}

git.gitUncommittedChanges in ThisBuild := true

val circeVersion = "0.9.0"
val akkaVersion = "2.5.24"
val akkaHttpVersion = "10.1.9"

val networkDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-parsing" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.bitlet" % "weupnp" % "0.1.4",
  "commons-net" % "commons-net" % "3.6"
)

val apiDependencies = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.20.0"
)

val loggingDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.3.0-alpha4"
)

val scorexUtil = "org.scorexfoundation" %% "scorex-util" % "0.1.6"

val testingDependencies = Seq(
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
  "org.scalactic" %% "scalactic" % "3.0.3" % "test",
  "org.scalatest" %% "scalatest" % "3.0.3" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.+",
  scorexUtil, (scorexUtil % Test).classifier("tests")
)

libraryDependencies ++= Seq(
  "com.iheart" %% "ficus" % "1.4.2",
  "org.scorexfoundation" %% "scrypto" % "2.1.7",
  scorexUtil
) ++ networkDependencies ++ apiDependencies ++ loggingDependencies ++ testingDependencies


scalacOptions ++= Seq("-Xfatal-warnings", "-feature", "-deprecation")

javaOptions ++= Seq(
  "-server"
)

testOptions in Test += Tests.Argument("-oD", "-u", "target/test-reports")

pomIncludeRepository := { _ => false }

val credentialFile = Path.userHome / ".ivy2" / ".credentials"
credentials ++= (for {
  file <- if (credentialFile.exists) Some(credentialFile) else None
} yield Credentials(file)).toSeq

lazy val testkit = Project(id = "testkit", base = file(s"testkit"))
  .dependsOn(basics)
  .settings(commonSettings: _*)

lazy val examples = Project(id = "examples", base = file(s"examples"))
  .dependsOn(basics, testkit)
  .settings(commonSettings: _*)

lazy val basics = Project(id = "scorex", base = file("."))
  .settings(commonSettings: _*)

credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq


// PGP key for signing a release build published to sonatype
// signing is done by sbt-pgp plugin
// how to generate a key - https://central.sonatype.org/pages/working-with-pgp-signatures.html
// how to export a key and use it with Travis - https://docs.scala-lang.org/overviews/contributors/index.html#export-your-pgp-key-pair
pgpPublicRing := file("ci/pubring.asc")
pgpSecretRing := file("ci/secring.asc")
pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toArray)
usePgpKeyHex("8F9FA382AAC56B7CEB1AEEC47D89C5BBC71D56BA")

//FindBugs settings

findbugsReportType := Some(FindbugsReport.PlainHtml)
