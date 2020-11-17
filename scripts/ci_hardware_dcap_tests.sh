#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh
source ${SCRIPT_DIR}/ci_hardware_common.sh

# Hardware tests using DCAP

# Teardown any aesmd container that might be left running, build and start the aesmd container.
# The driver is expected to already be installed and loaded on the CI agent.
teardownAESM
buildAESMImage $SCRIPT_DIR/../containers/aesmd/src/docker
startAESMContainer

# Run just the DCAP attestation tests.
# TODO This should be better handled using tags to discover unit tests that must be run on DCAP.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE -PhardwareTests conclave-host:test -i --tests com.r3.conclave.host.internal.HardwareAttestationTest"

# Note, the DCAP run doesn't test the samples as they can be run on the EPID machines which are cheaper.

# Run SDK tests in hardware using DCAP.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && ./test-sdk.sh hardware"

# Teardown AESM container
stopAndRemoveAESMImage

# Prune docker images
docker image prune -af