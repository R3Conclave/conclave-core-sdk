#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh
source ${SCRIPT_DIR}/ci_hardware_common.sh

# Prune docker images
docker image prune -af

sgx_mode=$1

if [ $sgx_mode != "Simulation" ]; then
    # Hardware tests
    # Teardown any aesmd container that might be left running, build and start the aesmd container.
    # The driver is expected to already be installed and loaded on the CI agent.
    teardownAESM
    buildAESMImage $SCRIPT_DIR/../containers/aesmd/src/docker
    startAESMContainer
fi

runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/integration-tests \
    && \$GRADLE -PenclaveMode=$sgx_mode -PslowTests test -i ${TEST_OPTS:-}"

if [ $sgx_mode != "Simulation" ]; then
    # Teardown AESM container
    stopAndRemoveAESMImage
fi