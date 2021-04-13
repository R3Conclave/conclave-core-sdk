#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_hardware_common.sh

loadAESMImage
docker load -i $script_dir/../containers/sgxjvm-build/src/docker/sgxjvm-build-docker-image.tar

release_mode="${1:-}"
conclave_version=`grep "conclave_version" ${script_dir}/../versions.gradle | cut -d"'" -f2`

if [ $release_mode == "release" ]; then
    docker tag $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/aesmd:latest $OBLIVIUM_CONTAINER_RELEASE_REGISTRY_URL/com.r3.sgx/aesmd:$conclave_version
    docker tag $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-build:latest $OBLIVIUM_CONTAINER_RELEASE_REGISTRY_URL/com.r3.sgx/sgxjvm-build:$conclave_version
    docker push $OBLIVIUM_CONTAINER_RELEASE_REGISTRY_URL/com.r3.sgx/aesmd:$conclave_version
    docker push $OBLIVIUM_CONTAINER_RELEASE_REGISTRY_URL/com.r3.sgx/sgxjvm-build:$conclave_version
else
    docker push $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/aesmd:latest
    docker push $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-build:latest
fi
