name              := "Backup"

organization      := "de.sciss"

version           := "0.1.0-SNAPSHOT"

scalaVersion      := "2.11.4"

licenses          := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt"))

description       := "Personal utility for making backups"

scalacOptions    ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "de.sciss" %% "desktop"   % "0.6.0",
  "de.sciss" %% "fileutil"  % "1.1.1",
  "de.sciss" %  "weblaf"    % "1.28"
)