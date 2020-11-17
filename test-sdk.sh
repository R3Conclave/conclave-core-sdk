#!/usr/bin/env bash

set -eou pipefail

./gradlew sdk

GRADLE_ARGS=""
if [ "${1:-}" == "hardware" ]; then
    GRADLE_ARGS="-PenclaveMode=debug"
fi

echo
echo Now trying to build and test the hello-world sample
echo
cd build/sdk/hello-world
./gradlew --stacktrace $GRADLE_ARGS host:test
./gradlew -q $GRADLE_ARGS host:installDist
./host/build/install/host/bin/host &
./gradlew --stacktrace -q client:run --args "abc"
