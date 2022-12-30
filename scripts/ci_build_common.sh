#!/usr/bin/env bash
set -euo pipefail
# Sets up common build script parameters and functions

code_host_dir=$PWD
code_docker_dir=${code_host_dir}

source ${code_host_dir}/containers/scripts/common.sh

container_image_repo="conclave-docker-dev.software.r3.com/com.r3.conclave"
docker_image_tag=$(${code_host_dir}/scripts/genDockerImageTag.sh)

# Docker container images
container_image_aesmd=$container_image_repo/aesmd:$docker_image_tag
container_image_conclave_build=$container_image_repo/conclave-build:$docker_image_tag
container_image_conclave_run=$container_image_repo/conclave-run:$docker_image_tag
container_image_integration_tests_build=$container_image_repo/integration-tests-build:$docker_image_tag
container_image_sdk_build=$container_image_repo/sdk-build:$docker_image_tag

mkdir -p $HOME/.gradle
mkdir -p $HOME/.m2
mkdir -p $HOME/.ccache
mkdir -p $HOME/.mx
mkdir -p $HOME/.container

# If running on a Linux host with SGX properly installed and configured,
# tunnel through the SGX driver and AESM daemon socket. This means you can
# still run the devenv on a non-SGX host or a Mac without it breaking.
sgx_hardware_flags=()

if [ -e /dev/sgx/enclave ] && [ -e /dev/sgx_enclave ]; then
    # /dev/sgx_enclave (and /dev/sgx_provision) is the current path used by the new in-kernel driver, available in Linux Kernel 5.11 >= and recommended by Intel:
    #    https://github.com/intel/SGXDataCenterAttestationPrimitives/blob/master/driver/linux/README.md
    # /dev/sgx/enclave is the path used by the old out-of-kernel driver, to be used with Linux Kernel < 5.11.
    # To keep backward compatibility, the path /dev/sgx/enclave is also available when the in-kernel driver is used, but in this case it just represents a symbolic link pointing to /dev/sgx_enclave.
    # We do not use out-of-kernel driver in Conclave and therefore, when /dev/sgx/enclave is available alone (i.e. not as a symbolic link), we consider the device as not supported by Conclave
    sgx_hardware_flags=("--device=/dev/sgx/enclave" "--device=/dev/sgx/provision" "-v" "/dev/sgx_provision:/dev_provision" "-v" "/dev/sgx_enclave:/dev/sgx_enclave" "-v" "/var/run/aesmd:/var/run/aesmd")
elif [ -e /dev/sgx/enclave ] && [ ! -e /dev/sgx_enclave ]; then
    echo "Out of kernel SGX device found in /dev/sgx/enclave but not supported by Conclave"
    exit 1
else
    echo "SGX device not found in /dev/sgx/enclave"
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
        ${3:-} \
        ${docker_opts[@]+"${docker_opts[@]}"} \
        ${container_image} \
        bash -c "$2"
}
