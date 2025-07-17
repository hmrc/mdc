import sbt._

object LibDependencies {
  val compileDependencies = Seq(
    "org.slf4j" % "slf4j-api" % "1.7.30"
  )

  val testDependencies = Seq(
    "ch.qos.logback"       %  "logback-classic" % "1.5.18" % Test,
    "org.apache.pekko"     %% "pekko-actor"     % "1.0.3"  % Test,
    "org.scalatest"        %% "scalatest"       % "3.2.17" % Test,
    "com.vladsch.flexmark" %  "flexmark-all"    % "0.64.8" % Test
  )
}
