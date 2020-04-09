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

mkdir -p /home/$(id -un)/.gradle
mkdir -p /home/$(id -un)/.ccache

SGX_HARDWARE_FLAGS=""
if [ -e /dev/isgx ] && [ -d /var/run/aesmd ]; then
    SGX_HARDWARE_FLAGS="--device=/dev/isgx -v /var/run/aesmd:/var/run/aesmd"
fi

function runDocker() {
    IMAGE_NAME=$1
    docker run --rm \
       -u $(id -u):$(id -g) \
       --network host \
       --group-add $(cut -d: -f3 < <(getent group docker)) \
       --ulimit core=512000000 \
       -v /home/$(id -un)/.gradle:/gradle \
       -v /home/$(id -un)/.ccache:/home/.ccache \
       -v ${CODE_HOST_DIR}:${CODE_DOCKER_DIR} \
       -v /var/run/docker.sock:/var/run/docker.sock \
       -v ${HOST_CORE_DUMP_DIR}:${HOST_CORE_DUMP_DIR} \
       ${SGX_HARDWARE_FLAGS} \
       -e GRADLE_USER_HOME=/gradle \
       $(env | cut -f1 -d= | grep OBLIVIUM_ | sed 's/^OBLIVIUM_/-e OBLIVIUM_/') \
       ${OBLIVIUM_CONTAINER_REGISTRY_URL}/${IMAGE_NAME} \
       bash -c "GRADLE='./gradlew $EXCLUDE_NATIVE'; $2"
}
