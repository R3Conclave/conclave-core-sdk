#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

# Cleanup local k8s cluster from previous end-to-end test run
kubectl delete namespace test || true

# Then run the tests. We expose the host network so that the test container can connect to the k8s cluster directly.
# Publish artifacts so they can be used by the samples.
TEST_OPTS=${TEST_OPTS:-}
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE test publish sdkFiles sdk -i $TEST_OPTS \
    && cd $CODE_DOCKER_DIR/samples && \$GRADLE --refresh-dependencies test -i"

# Run the SDK tests.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && ./test-sdk.sh"

# Now ensure that we build the Release enclave artifacts.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/samples && \$GRADLE buildSignedEnclaveRelease -i"
