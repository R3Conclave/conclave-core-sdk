#!/usr/bin/env bash

set -eou pipefail

./gradlew sdk

echo
echo Running the integration tests
echo
cd integration-tests
# TODO This should be better handled using tags to discover unit tests that must be run on DCAP.
./gradlew --stacktrace -PenclaveMode=debug general:tests:test --tests com.r3.conclave.integrationtests.general.tests.AttestationTests
