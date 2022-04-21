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
        docker save "$image" | gzip > "$file_name"
    fi
}
#####################################################################################################
# Copy the setup-users-sh script. The file is being copied to reduce duplication
root_script_dir="${code_host_dir}/containers/graalvm-build/docker/root/scripts"
# Ensure the directory exists
mkdir -p ${root_script_dir}
# Copy the file
cp "${code_host_dir}/../containers/sdk-build/src/docker/root/scripts/setup-users.sh" "${root_script_dir}/setup-users.sh"
#####################################################################################################
# Build the docker image based on the Dockerfile and save it to a file so it can be loaded when required
# This will reduced the time required to run a container in TeamCity because the docker image is built once
# and it can be loaded by the different subprojects such as "SDK Unit Tests and build", "SDK Integration Tests (Debug)",...
build_dir="${code_host_dir}/build"
dockerfile_dir="${code_host_dir}/containers/graalvm-build/docker"
docker_image_name_tag="$OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/graalvm-build:latest"
docker_image_file="$build_dir/containers/graalvm-build-docker-image.tar.gz"
pushd "$dockerfile_dir"
docker build -t "$docker_image_name_tag" .
saveDockerImage "$docker_image_file" "$docker_image_name_tag"
popd
#####################################################################################################
