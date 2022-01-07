#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

function teardownAESM() {
    docker stop aesmd || true
}

function loadAESMImage() {
    if [ -z "${DOCKER_IMAGE_LOAD:-}" ] || [ "${DOCKER_IMAGE_LOAD}" == "1" ]; then
        docker load -i $code_host_dir/containers/aesmd/build/aesmd-docker-image.tar.gz
    fi
}

function startAESMContainer() {
    docker run -d --rm --name aesmd ${sgx_hardware_flags[@]+"${sgx_hardware_flags[@]}"} ${OBLIVIUM_CONTAINER_REGISTRY_URL}/com.r3.sgx/aesmd:latest
}
