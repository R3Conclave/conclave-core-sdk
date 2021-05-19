#!/usr/bin/env bash

set -eou pipefail

sgx_mode=$1

gradle_args="-PenclaveMode=$sgx_mode"

echo
echo Now trying to build and test the hello-world sample
echo
pushd build/distributions/conclave-sdk-*/hello-world
./gradlew --stacktrace $gradle_args host:test
./gradlew -q $gradle_args host:installDist
./host/build/install/host/bin/host &
./gradlew --stacktrace -q client:run --args "abc"
popd

export USER=`whoami`
export HOME=/home/$USER

if [ -e $JAVA_HOME/jre/lib/rt.jar ]; then
  echo
  echo Testing the CorDapp sample
  echo

  pushd build/distributions/conclave-sdk-*/cordapp
  ./gradlew workflows:test
  popd
else
  echo
  echo "\$JAVA_HOME ($JAVA_HOME) doesn't seem to be a Java 8, so skipping the CorDapp sample."
  echo
fi
