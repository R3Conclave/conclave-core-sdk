#!/usr/bin/env bash

set -eou pipefail
if [ "${1:-}" != "hardware" ]; then
    echo Building the SDK Zip file.
    echo
    ./gradlew -i --stacktrace sdk
    echo
    echo
    echo Now trying to build and test the hello-world sample
    echo
    (
    cd build/sdk/hello-world;
    ./gradlew -i --stacktrace --no-daemon test host:run
    )
else
    echo Test the sample in debug mode
    (
    cd build/sdk/conclave-sdk-*/hello-world;
    ./gradlew -i --stacktrace --no-daemon -PenclaveMode=debug -Pspid=***REMOVED*** -Pattestation-key=***REMOVED*** test
    )
fi