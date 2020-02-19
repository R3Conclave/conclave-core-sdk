#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

# Cleanup local k8s cluster from previous end-to-end test run
kubectl delete namespace test || true

# Login and pull the current build image
docker login ${OBLIVIUM_CONTAINER_REGISTRY_URL} -u ${OBLIVIUM_CONTAINER_REGISTRY_USERNAME} -p ${OBLIVIUM_CONTAINER_REGISTRY_PASSWORD}
docker pull ${OBLIVIUM_CONTAINER_REGISTRY_URL}/com.r3.sgx/sgxjvm-build

# Refresh dependencies
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/samples && \$GRADLE --refresh-dependencies -i"

# First we build the build-image itself in case this build changes it
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE containers:sgxjvm-build:buildImagePublish"

# Then run the tests. We expose the host network so that the test container can connect to the k8s cluster directly.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE test -i && cd $CODE_DOCKER_DIR/samples && \$GRADLE test -i -x end-to-end-test:test \
    && cd $CODE_DOCKER_DIR/test-infrastructure && \$GRADLE test -i"

# Run the sgx-jvm-plugin tests.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/sgx-jvm-plugin && \$GRADLE test -i"

# Now ensure that we build the Release enclave artifacts.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/samples && \$GRADLE buildSignedEnclaveRelease -i"
