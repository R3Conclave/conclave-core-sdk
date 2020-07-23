#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

# Prune docker images
docker image prune -af

# Login and pull the current build image
docker login ${OBLIVIUM_CONTAINER_REGISTRY_URL} -u ${OBLIVIUM_CONTAINER_REGISTRY_USERNAME} -p ${OBLIVIUM_CONTAINER_REGISTRY_PASSWORD}
docker pull ${OBLIVIUM_CONTAINER_REGISTRY_URL}/com.r3.sgx/sgxjvm-build

# Refresh dependencies
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE --refresh-dependencies -i"

# Publish docker images with latest tag
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE -i containers:publishAsLatest"