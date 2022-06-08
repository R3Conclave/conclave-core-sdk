#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Publish. All testing should be done before this, i.e. running ci_build.sh
runDocker com.r3.sgx/sdk-build "./gradlew publish -i"
