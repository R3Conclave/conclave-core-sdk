#!/usr/bin/env bash

set -eou pipefail

./gradlew sdk

GRADLE_ARGS=""
if [ "${1:-}" == "hardware" ]; then
    GRADLE_ARGS="-PenclaveMode=debug"
fi

echo
echo Running the integration tests
echo
cd integration-tests
./gradlew --stacktrace $GRADLE_ARGS test
