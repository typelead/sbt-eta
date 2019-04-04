#!/usr/bin/env sh

set -e

SBT_VERSION=1.2.8
apt-get update
apt-get install -y curl
curl -fsL "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | tar xfz - -C /usr/local
ln -s /usr/local/sbt/bin/* /usr/local/bin/