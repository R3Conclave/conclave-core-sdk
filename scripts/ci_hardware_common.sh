#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

function teardownAESM() {
    docker stop aesmd || true
}

function startAESMContainer() {
    docker run -d --rm --name aesmd ${sgx_hardware_flags[@]+"${sgx_hardware_flags[@]}"} $container_image_aesmd
}
