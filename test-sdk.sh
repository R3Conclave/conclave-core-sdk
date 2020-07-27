#!/usr/bin/env bash

set -eou pipefail

./gradlew sdk

SPID=***REMOVED***
AK=***REMOVED***
GRADLE_ARGS=""
if [ "${1:-}" == "hardware" ]; then
    GRADLE_ARGS="-PenclaveMode=debug -Pspid=$SPID -Pattestation-key=$AK"
fi

echo
echo Now trying to build and test the hello-world sample
echo
cd build/sdk/hello-world
./gradlew --stacktrace $GRADLE_ARGS host:test
./gradlew -q $GRADLE_ARGS host:installDist
./host/build/install/host/bin/host $SPID $AK &
./gradlew --stacktrace -q client:run --args "abc"
