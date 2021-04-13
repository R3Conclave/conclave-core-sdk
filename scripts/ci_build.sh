#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

# Cleanup local k8s cluster from previous end-to-end test run
kubectl delete namespace test || true

loadBuildImage

# Then run the tests. We expose the host network so that the test container can connect to the k8s cluster directly.
TEST_OPTS=${TEST_OPTS:-}
runDocker com.r3.sgx/sgxjvm-build "./gradlew test sdk -i $TEST_OPTS"
