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
TEST_OPTS=${TEST_OPTS:--x end-to-end-test:test -x :djvm:djvm-host:test}
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE test -i && cd $CODE_DOCKER_DIR/samples && \$GRADLE test -i $TEST_OPTS"

# Hardware tests
# Teardown any aesmd container that might be left running, build and start the aesmd container.
# Run the tests and teardown the aesmd afterwards.
# The driver is expected to already be installed and loaded on the CI agent.
docker stop aesmd || true
docker rm aesmd || true
cd containers/aesmd/src/docker && docker build --no-cache -t localhost:5000/com.r3.sgx/aesmd .
docker run -d --rm --name aesmd ${SGX_HARDWARE_FLAGS} localhost:5000/com.r3.sgx/aesmd
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/samples && \$GRADLE -Psgx_mode=Debug test -i $TEST_OPTS"

# Run the SDK tests.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && ./test-sdk.sh"

docker stop aesmd
docker rmi localhost:5000/com.r3.sgx/aesmd

# Now ensure that we build the Release enclave artifacts.
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/samples && \$GRADLE buildSignedEnclaveRelease -i"
