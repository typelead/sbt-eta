#!/usr/bin/env sh

set -e

SCALA_VERSION=2.12.5
SBT_VERSION=1.2.8

# Install scala
curl -fsL http://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /usr/local
ln -s /usr/local/scala-$SCALA_VERSION/bin/* /usr/local/bin/

# Install sbt
curl -fsL "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | tar xfz - -C /usr/local
mv "/usr/local/sbt-launcher-packaging-$SBT_VERSION" /usr/local/sbt
ln -s /usr/local/sbt/bin/* /usr/local/bin/