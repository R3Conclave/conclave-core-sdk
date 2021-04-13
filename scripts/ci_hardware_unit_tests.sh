#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh
source ${script_dir}/ci_hardware_common.sh

loadBuildImage

# Teardown any aesmd container that might be left running, build and start the aesmd container.
# The driver is expected to already be installed and loaded on the CI agent.
teardownAESM
loadAESMImage
startAESMContainer

# Run the hardware unit tests.
runDocker com.r3.sgx/sgxjvm-build "./gradlew -PhardwareTests test -i ${TEST_OPTS:-}"

# Teardown AESM container
teardownAESM
