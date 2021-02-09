#!/usr/bin/env bash

set -eou pipefail

./gradlew sdkFiles

GRADLE_ARGS=""
if [ "${1:-}" == "hardware" ]; then
    GRADLE_ARGS="-PenclaveMode=debug"
fi

echo
echo Now trying to build and test the hello-world sample
echo
SDK_DIR=$PWD/build/sdk
cd build/sdk/hello-world
./gradlew --stacktrace $GRADLE_ARGS host:test
./gradlew -q $GRADLE_ARGS host:installDist
./host/build/install/host/bin/host &
./gradlew --stacktrace -q client:run --args "abc"

# TODO: Fix the integration tests for the CorDapp and remove this line. The issue is a Docker build-env container versioning
# issue meaning /home/$user isn't created.
exit 0

if [ -e $JAVA_HOME/jre/lib/rt.jar ]; then
  echo
  echo Testing the CorDapp sample
  echo

  cd ../cordapp
  ./gradlew workflows:test
else
  echo
  echo "\$JAVA_HOME ($JAVA_HOME) doesn't seem to be a Java 8, so skipping the CorDapp sample."
  echo
fi