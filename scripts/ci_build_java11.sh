#!/usr/bin/env bash
set -xeuo pipefail

# This configuration is used as a regression test to make sure PR changes do not break
# Java 11 compatibility. See java11.md in the internal docs for details.
#
# A full composite build is performed but no artifacts are published. The samples use
# the local SDK composite build rather than pulling published artifacts
#
# If the build changes the build-image itself then this is published before the remainder
# of the build commences in order to ensure the correct build environment

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

# Login and pull the current build image
docker login ${OBLIVIUM_CONTAINER_REGISTRY_URL} -u ${OBLIVIUM_CONTAINER_REGISTRY_USERNAME} -p ${OBLIVIUM_CONTAINER_REGISTRY_PASSWORD}
docker pull ${OBLIVIUM_CONTAINER_REGISTRY_URL}/com.r3.sgx/sgxjvm-build

# Refresh dependencies
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE --refresh-dependencies -i"

# First we build the build-image itself in case this build changes it
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE containers:sgxjvm-build:buildImagePublish"

# Then build and run the tests. Note that the SDK build excludes dokka due to the dokka plugin not supporting
# Java 11. 
TEST_OPTS=${TEST_OPTS:-}
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 JVMCI_HOME=\$LABSJDK_HOME \$GRADLE test sdk -PexcludeDokka $TEST_OPTS -i \
    && cd $CODE_DOCKER_DIR/samples && JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \$GRADLE --refresh-dependencies test -i \
    && cd $CODE_DOCKER_DIR/build/sdk/hello-world && JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \$GRADLE test host:run -i"
