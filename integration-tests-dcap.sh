#!/usr/bin/env bash

set -eou pipefail

echo
echo Running the integration tests
echo
pushd integration-tests
# TODO This should be better handled using tags to discover unit tests that must be run on DCAP.
./gradlew --stacktrace -PenclaveMode=debug general:tests:test --tests com.r3.conclave.integrationtests.general.tests.AttestationTests
popd
