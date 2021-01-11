#!/usr/bin/env bash
set -xeuo pipefail
# Sets up common build script parameters and functions

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))

HOST_CORE_DUMP_DIR=/var/crash
CODE_HOST_DIR=$PWD
export CONTAINER_NAME=$(echo "code${CODE_HOST_DIR}" | sed -e 's/[^a-zA-Z0-9_.-]/_/g')
CODE_DOCKER_DIR=${CODE_HOST_DIR}

if [[ ${OBLIVIUM_BUILD_BRANCH:-} =~ .*/exclude-native$ ]]
then
    export EXCLUDE_NATIVE="-PexcludeNative=true"
else
    export EXCLUDE_NATIVE=""
fi
export GRADLE_OPTS="-Dorg.gradle.workers.max=$(nproc) --stacktrace"

mkdir -p /home/$(id -un)/.gradle
mkdir -p /home/$(id -un)/.m2
mkdir -p /home/$(id -un)/.ccache
mkdir -p /home/$(id -un)/.mx

SGX_HARDWARE_FLAGS=""
if [ -e /dev/isgx ] && [ -d /var/run/aesmd ]; then
    SGX_HARDWARE_FLAGS="--device=/dev/isgx -v /var/run/aesmd:/var/run/aesmd"
elif [ -e /dev/sgx/enclave ] && [ -e /dev/sgx/provision ]; then
    if [ -d /var/run/aesmd ]; then
        SGX_HARDWARE_FLAGS="--device=/dev/sgx/enclave --device=/dev/sgx/provision -v /var/run/aesmd:/var/run/aesmd"
    else
        SGX_HARDWARE_FLAGS="--device=/dev/sgx/enclave --device=/dev/sgx/provision"
    fi
fi

# Part of Graal build process involves cloning and running git commands.
# TeamCity is configured to use mirrors (https://www.jetbrains.com/help/teamcity/git.html#Git-AgentSettings),
# and for the git commands to work properly, the container needs access
# the agent home directory.
AGENT_HOME_DIR_FLAGS=""
if [ -d ${AGENT_HOME_DIR:-} ]; then
    AGENT_HOME_DIR_FLAGS="-v ${AGENT_HOME_DIR}:/${AGENT_HOME_DIR}"
fi

# `PROJECT_VERSION` unset means that the composite build will be used by the samples.
# `PROJECT_VERSION` set to empty will make samples use published artifacts with hardcoded default version,
#  e.g. 0.3-SNAPSHOT, as used by the `master` builds and the Java 11 PR build.
# `PROJECT_VERSION` set to non-empty will make the samples use the artifacts published with said version,
#  as used by the PR hardware builds.
# `PROJECT_VERSION` should only be passed to the container if set, even if empty, otherwise leave it unset.
# This should be in tune with the CI configurations.
PROJECT_VERSION_FLAGS=""
if [ -n "${PROJECT_VERSION-}" ] || [ -n "${PROJECT_VERSION+empty}" = "empty" ]; then
    PROJECT_VERSION_FLAGS="-e PROJECT_VERSION=${PROJECT_VERSION}"
fi

function runDocker() {
    IMAGE_NAME=$1
    docker run --rm \
       -u $(id -u):$(id -g) \
       --network host \
       --group-add $(cut -d: -f3 < <(getent group docker)) \
       --ulimit core=512000000 \
       -v /home/$(id -un)/.gradle:/gradle \
       -v /home/$(id -un)/.m2:/home/.m2 \
       -v /home/$(id -un)/.mx:/home/.mx \
       -v /home/$(id -un)/.ccache:/home/.ccache \
       -v ${CODE_HOST_DIR}:${CODE_DOCKER_DIR} \
       -v /var/run/docker.sock:/var/run/docker.sock \
       -v ${HOST_CORE_DUMP_DIR}:${HOST_CORE_DUMP_DIR} \
       $AGENT_HOME_DIR_FLAGS \
       ${SGX_HARDWARE_FLAGS} \
       ${PROJECT_VERSION_FLAGS} \
       -e GRADLE_USER_HOME=/gradle \
       $(env | cut -f1 -d= | grep OBLIVIUM_ | sed 's/^OBLIVIUM_/-e OBLIVIUM_/') \
       ${OBLIVIUM_CONTAINER_REGISTRY_URL}/${IMAGE_NAME} \
       bash -c "GRADLE='./gradlew $EXCLUDE_NATIVE'; $2"
}

# Prune docker images
docker image prune -af

# Login and pull the current build image
docker login ${OBLIVIUM_CONTAINER_REGISTRY_URL} -u ${OBLIVIUM_CONTAINER_REGISTRY_USERNAME} -p ${OBLIVIUM_CONTAINER_REGISTRY_PASSWORD}
docker pull ${OBLIVIUM_CONTAINER_REGISTRY_URL}/com.r3.sgx/sgxjvm-build

# Refresh dependencies
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE --refresh-dependencies -i"

# First we build the build-image itself in case this build changes it
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE containers:sgxjvm-build:buildImagePublish"
