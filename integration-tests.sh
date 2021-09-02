#!/usr/bin/env bash

set -eou pipefail

source integration-tests-common.sh

# TODO; Don't do this!
# A proper solution would be to modify both integration-tests/build.gradle and then also
# the CI scripts accordingly, so that the ./gradlew sdk command outputs the sdk where the
# tests expect to find them, rather than doing this nonsense.
extractCurrentDistribution

GRADLE_ARGS=""
if [ "${1:-}" == "hardware" ]; then
    GRADLE_ARGS="-PenclaveMode=debug"
fi

echo
echo Running the integration tests
echo
pushd integration-tests
./gradlew --stacktrace $GRADLE_ARGS test
popd
