#!/usr/bin/env bash
set -xeuo pipefail

# $HOME in our container doesn't actually reflect the home directory that gradle publishes
# mavenLocal repos into.
REPO_HOME=/home/`whoami`/.m2/repository/org/jetbrains/dokka
rm -fr $REPO_HOME
rm -fr dokka

git clone --depth 1 https://github.com/R3Conclave/dokka.git -b "conclave-changes"
cd dokka
./gradlew publishToMavenLocal -x test -x integrationTest
cd ..

pushd $REPO_HOME
zip -r dokka.zip .
popd
mv $REPO_HOME/dokka.zip .