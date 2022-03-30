#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

function saveDockerImage() {
    if [ -z "${DOCKER_IMAGE_SAVE:-}" ] || [ "${DOCKER_IMAGE_SAVE}" == "1" ]; then
        local file_name="$1"
        local dir="$(dirname $file_name)"
        local image="$2"
        mkdir -p "$dir"
        # The docker image is saved to a compressed file to minimize its size as much as possible. Additionally,
        # the compressed file is split into smaller files of up to 500MB. This is done because Teamcity sometimes struggles
        # to copy large files between different agents.
        docker save $image | gzip | split -b 500M - "$file_name.part"
    fi
}

sgxjvm_build_dir="${code_host_dir}/containers/sgxjvm-build/build"

pushd "${code_host_dir}/containers/sgxjvm-build/src/docker"
docker build -t $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-build:latest .
saveDockerImage "$sgxjvm_build_dir/sgxjvm-build-docker-image.tar.gz" $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-build:latest
popd

if [ -z "${DOCKER_IMAGE_AESMD_BUILD:-}" ] || [ "${DOCKER_IMAGE_AESMD_BUILD}" == "1" ]; then
    aesmd_build_dir="${code_host_dir}/containers/aesmd/build"
    pushd "${code_host_dir}/containers/aesmd/src/docker"
    docker build -t $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/aesmd:latest .
    saveDockerImage "$aesmd_build_dir/aesmd-docker-image.tar.gz" $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/aesmd:latest
    popd
fi