#!/usr/bin/env bash

set -eou pipefail

source integration-tests-common.sh

# TODO; Don't do this!
# A proper solution would be to modify both integration-tests/build.gradle and then also
# the CI scripts accordingly, so that the ./gradlew sdk command outputs the sdk where the
# tests expect to find them, rather than doing this nonsense.
extractCurrentDistribution

echo
echo Running the integration tests
echo
pushd integration-tests
# TODO This should be better handled using tags to discover unit tests that must be run on DCAP.
./gradlew --stacktrace -PenclaveMode=debug general:tests:test --tests com.r3.conclave.integrationtests.general.tests.AttestationTests
popd
