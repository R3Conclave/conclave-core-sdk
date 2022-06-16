#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

loadBuildImage

# Then run the build
runDocker com.r3.sgx/graalvm-build "./gradlew tarGraal --info"
