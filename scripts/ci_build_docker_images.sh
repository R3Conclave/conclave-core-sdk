#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh
source ${script_dir}/devenv_envs.sh

function saveDockerImage() {
    if [ -z "${DOCKER_IMAGE_SAVE:-}" ] || [ "${DOCKER_IMAGE_SAVE}" == "1" ]; then
        local file_name="$1"
        local dir="$(dirname $file_name)"
        local image="$2"
        mkdir -p "$dir"
        # The docker image is saved to a compressed file to minimize its size as much as possible. Additionally,
        # the compressed file is split into smaller files of up to 500MB. This is done because Teamcity sometimes struggles
        # to copy large files between different agents.
        docker save "$image" | gzip | split -b 500M - "$file_name.part"
    fi
}

# Graal version without the patch versioning number
graal_version='21.3'
graal_group_id=com/r3/conclave/graal
graal_artifact_id=graal-sdk
graal_artifact_path=$graal_group_id/$graal_artifact_id/$graal_version/$graal_artifact_id-$graal_version.tar.gz

sgxjvm_build_dir="${code_host_dir}/containers/sgxjvm-build/build"
sgxjvm_downloads_dir="root/downloads"
graal_tar_file="${sgxjvm_downloads_dir}/graal-sdk.tar.gz"

pushd "${code_host_dir}/containers/sgxjvm-build/src/docker"
# Delete all files from the directory to avoid issues
rm -rf $sgxjvm_downloads_dir/*

# If you are upgrading Graal version consider setting the environment variable GRAAL_DIR to the local directory where Graal was built.
# For instance, GRAAL_DIR=../../../../graal/build/distributions/graal-sdk.tar.gz. Keep in mind the working directory when setting the environment variable.
if [[ -z "${GRAAL_DIR:-}" ]]; then
  credentials=$OBLIVIUM_MAVEN_USERNAME:$OBLIVIUM_MAVEN_PASSWORD
  url=$OBLIVIUM_MAVEN_URL/$OBLIVIUM_MAVEN_REPOSITORY/${graal_artifact_path}
  curl -SLf -o ${graal_tar_file} --create-dirs -u $credentials  $url
else
  # Ensure the directory exists
  mkdir -p $sgxjvm_downloads_dir

  # This copy is required to allow docker to access the file
  echo "Working directory:" $(pwd)
  cp $GRAAL_DIR $graal_tar_file
fi

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