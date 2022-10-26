#!/usr/bin/env bash
set -euo pipefail
# Sets up common build script parameters and functions

code_host_dir=$PWD
code_docker_dir=${code_host_dir}

source ${code_host_dir}/containers/scripts/common.sh

conclave_graal_version=$(grep -w "conclave_graal_version =" ./versions.gradle | cut -d '=' -f 2 | sed "s/[ ']//g")
artifact_path=$conclave_graal_group_id/$conclave_graal_artifact_id/$conclave_graal_version/$conclave_graal_artifact_id-$conclave_graal_version.tar.gz.sha512
url="https://software.r3.com/artifactory/conclave-maven/${artifact_path}"

conclave_graal_sha512sum=$(curl -SLf $url)

# Generate the docker image tag based on the contents inside the containers module
# Please be sure that any script that might change the final docker container image
# is inside the folder containers/scripts. Otherwise, the tag generated won't be
# correct and you run the risk of overwriting existing docker images that are used
# by older release branches. Keep in mind that temporary or build directories should be excluded
# The following code generates the hash based on the contents of a directory and the version of graal used.
# This hash takes into account the contents of each file inside the directory and subdirectories
# The cut command removes the dash at the end.
# All subdirectories with name build and download and hidden files are excluded. Please be sure that any file
# that is not tracked by git should not be included in this hash.
# In order to allow ci_build_publish_docker_images to detect automatically the new version of graal, the hash generated
# must include the conclave_graal sha512sum as well.
pushd ${code_host_dir}
containers_dir_hash=$(find ./containers \( ! -regex '.*/\..*\|.*/build/.*\|.*/downloads/.*' \) -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -d ' ' -f1)
docker_image_tag=$(echo $containers_dir_hash-$conclave_graal_sha512sum | sha256sum | cut -d ' ' -f1)
popd

# Docker container images repository
container_image_repo=conclave-docker-dev.software.r3.com/com.r3.conclave

# Docker container images
container_image_conclave_build=$container_image_repo/conclave-build:$docker_image_tag
container_image_integration_tests_build=$container_image_repo/integration-tests-build:$docker_image_tag
container_image_sdk_build=$container_image_repo/sdk-build:$docker_image_tag

mkdir -p $HOME/.gradle
mkdir -p $HOME/.m2
mkdir -p $HOME/.ccache
mkdir -p $HOME/.mx
mkdir -p $HOME/.container

# If running on a Linux host with SGX properly installed and configured,
# tunnel through the SGX driver and AES daemon socket. This means you can
# still run the devenv on a non-SGX host or a Mac without it breaking.
sgx_hardware_flags=()
if [ -e /dev/isgx ]; then
    sgx_hardware_flags=("--device=/dev/isgx")
elif [ -e /dev/sgx_enclave ]; then  # DCAP
    sgx_hardware_flags=("--device=/dev/sgx_enclave" "--device=/dev/sgx_provision")
elif [ -e /dev/sgx/enclave ]; then  # Legacy DCAP driver location
    sgx_hardware_flags=("--device=/dev/sgx/enclave" "--device=/dev/sgx/provision")
fi

docker_group_add=()

# OS specific settings
if [ "$(uname)" == "Darwin" ]; then
    num_cpus=$( sysctl -n hw.ncpu )
    docker_ip="192.168.65.2"
    network_cmd=("-p" "8000:8000" "-p" "8001:8001")
    host_core_dump_dir="/cores/"
else
    docker_gid=$(cut -d: -f3 < <(getent group docker))
    if [[ "$docker_gid" == "" ]]; then
        echo "You don't appear to have a docker UNIX group configured. This script requires one."
        echo
        echo "Follow the post-install instructions at https://docs.docker.com/install/linux/linux-postinstall/"
        echo "to finish the Docker setup process."
        exit 1
    fi
    docker_group_add=("--group-add" "${docker_gid}")
    num_cpus=$( nproc )
    network_cmd=("--network=host")
    host_core_dump_dir="/var/crash/"

    if [[ $(uname -r) == *microsoft* ]]; then
        docker_ip="172.17.0.2"
    else
        docker_ip=$(ip address show docker0 2> /dev/null | sed -n 's/^.*inet \(addr:[ ]*\)*\([^ ]*\).*/\2/p' | cut -d/ -f1)
        if [ -z "$docker_ip" ]; then
            docker_ip="172.17.0.2"
        fi
    fi
fi

sgx_prv_gid=$(cut -d: -f3 < <(getent group sgx_prv))
if [ -n "$sgx_prv_gid" ]; then
    docker_group_add+=("--group-add" "${sgx_prv_gid}")
fi

# Beware of the array expansion pattern ${@+"$@"}.
# This is the only safe way to expand an empty array in all bash versions.
# For more information: https://gist.github.com/dimo414/2fb052d230654cc0c25e9e41a9651ebe
docker_opts=(\
    "--rm" \
    "-u" "$(id -u):$(id -g)" \
    "--ulimit" "core=512000000" \
    ${docker_group_add[@]+"${docker_group_add[@]}"} \
    ${network_cmd[@]+"${network_cmd[@]}"} \
    "-v" "$HOME/.gradle:/gradle" \
    "-v" "$HOME/.m2:/home/.m2" \
    "-v" "$HOME/.mx:/home/.mx" \
    "-v" "$HOME/.ccache:/home/.ccache" \
    "-v" "$HOME/.container:/home" \
    "-v" "/var/run/docker.sock:/var/run/docker.sock" \
    "-v" "$host_core_dump_dir:/var/crash/" \
    "-v" "${code_host_dir}:${code_docker_dir}" \
    ${sgx_hardware_flags[@]+"${sgx_hardware_flags[@]}"} \
    "-e" "GRADLE_USER_HOME=/gradle" \
    "-e" "GRADLE_OPTS=-Dorg.gradle.workers.max=$num_cpus" \
    $(env | cut -f1 -d= | awk 'gsub(/^CONCLAVE_/, "-e CONCLAVE_")') \
    "-w" "$code_docker_dir" \
)

function runDocker() {
    container_image=$1
    docker run \
        ${docker_opts[@]+"${docker_opts[@]}"} \
        ${container_image} \
        bash -c "$2"
}
