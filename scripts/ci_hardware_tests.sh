#!/usr/bin/env bash
set -xeuo pipefail

# TODO Delete this once the hardware build pipeline has been updated to use ci_hardware_epid_tests.sh and ci_hardware_dcap_tests.sh

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh
source ${SCRIPT_DIR}/ci_hardware_common.sh

# Hardware tests
# Teardown any aesmd container that might be left running, build and start the aesmd container.
# The driver is expected to already be installed and loaded on the CI agent.
teardownAESM
buildAESMImage $SCRIPT_DIR/../containers/aesmd/src/docker
startAESMContainer

# Run the hardware unit tests.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE -PhardwareTests test -i ${TEST_OPTS:-}"

# Run the samples tests.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/samples \
    && \$GRADLE -Psgx_mode=Debug test -i ${TEST_OPTS:-}"

# Test SDK tests in hardware.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && ./test-sdk.sh hardware && ./integration-tests.sh hardware"

# Teardown AESM container
stopAndRemoveAESMImage

# Prune docker images
docker image prune -af