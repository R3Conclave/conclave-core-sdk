#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

runDocker com.r3.sgx/sdk-build "./gradlew -PrunPluginEnclaveGradleTests :plugin-enclave-gradle:test -i ${TEST_OPTS:-}"
