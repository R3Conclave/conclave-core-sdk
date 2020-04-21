#!/usr/bin/env bash

set -eou pipefail

echo Building the SDK files without bothering to zip them.
echo
./gradlew sdkFiles
echo
echo
echo Now trying to build and test the hello-world sample
echo
(
cd build/sdk/hello-world;
./gradlew --no-daemon test host:run
)
echo Test the sample in debug mode
(
cd build/sdk/hello-world;
./gradlew --no-daemon -PenclaveMode=debug -Pspid=***REMOVED*** -Pattestation-key=***REMOVED*** test
)
