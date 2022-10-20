#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh
source ${script_dir}/ci_hardware_common.sh

enclaveMode=$1

if [ $enclaveMode != "Simulation" ]; then
    # Hardware tests
    # Teardown any aesmd container that might be left running, build and start the aesmd container.
    # The driver is expected to already be installed and loaded on the CI agent.
    teardownAESM
    startAESMContainer
fi

runDocker $container_image_conclave_build_integration_tests "cd integration-tests && ./gradlew -PenclaveMode=$enclaveMode test -s -i"

if [ $enclaveMode != "Simulation" ]; then
    # Teardown AESM container
    teardownAESM
fi
