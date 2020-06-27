#!/bin/bash

script_dir=$(dirname "${BASH_SOURCE[0]}")
export JAVA_OPTS="$JAVA_OPTS -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1080"
sudo systemctl start docker.service
sbt "client/runMain cl.monsoon.star.client.data.CnDomainSuffixCollector $script_dir/../client/src/main/resources; \
client/runMain cl.monsoon.star.client.data.CnIpCidrCollector $script_dir/../client/src/main/resources; \
link/universal:packageBin; \
link/docker:publish"
sudo systemctl stop docker.service
