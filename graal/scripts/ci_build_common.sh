#!/usr/bin/env bash
set -euo pipefail
# Sets up common build script parameters and functions

code_host_dir=$PWD
code_docker_dir=${code_host_dir}

mkdir -p $HOME/.gradle
mkdir -p $HOME/.m2
mkdir -p $HOME/.ccache
mkdir -p $HOME/.mx
mkdir -p $HOME/.container

# USE_MAVEN_REPO can be set to "artifactory" or "sdk" and will affect
# which artifacts the samples will use.
# When unset, samples will use the composite build.
use_maven_repo_flags=()
if [ -n "${USE_MAVEN_REPO-}" ]; then
    use_maven_repo_flags=("-e" "USE_MAVEN_REPO=${USE_MAVEN_REPO}")
fi

docker_group_add=()
# OS specific settings
if [ "$(uname)" == "Darwin" ]; then
    cardreader_gid=""
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
    cardreader_gid=$(cut -d: -f3 < <(getent group cardreader) || echo "")
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

volume_usb=()
if [[ -d /dev/bus/usb ]]; then
    volume_usb=("-v" "/dev/bus/usb:/dev/bus/usb")
fi

group_cardreader=()
if [[ ! -z ${cardreader_gid} ]]; then
    group_cardreader=("--group-add" "${cardreader_gid}")
fi

# Part of Graal build process involves cloning and running git commands.
# TeamCity is configured to use mirrors (https://www.jetbrains.com/help/teamcity/git.html#Git-AgentSettings),
# and for the git commands to work properly, the container needs access
# the agent home directory.
agent_home_dir_flags=()
if [ -d "${AGENT_HOME_DIR:-}" ]; then
  agent_home_dir_flags=("-v" "${AGENT_HOME_DIR}/system:/${AGENT_HOME_DIR}/system")
fi

# Beware of the array expansion pattern ${@+"$@"}.
# This is the only safe way to expand an empty array in all bash versions.
# For more information: https://gist.github.com/dimo414/2fb052d230654cc0c25e9e41a9651ebe
docker_opts=(\
    "--rm" \
    "--privileged" \
    "-u" "$(id -u):$(id -g)" \
    "--ulimit" "core=512000000" \
    "--label" "graalvm" \
    ${docker_group_add[@]+"${docker_group_add[@]}"} \
    ${network_cmd[@]+"${network_cmd[@]}"} \
    ${group_cardreader[@]+"${group_cardreader[@]}"} \
    "-v" "$HOME/.gradle:/gradle" \
    "-v" "$HOME/.m2:/home/.m2" \
    "-v" "$HOME/.mx:/home/.mx" \
    "-v" "$HOME/.ccache:/home/.ccache" \
    "-v" "$HOME/.container:/home" \
    "-v" "/var/run/docker.sock:/var/run/docker.sock" \
    "-v" "$host_core_dump_dir:/var/crash/" \
    "-v" "${code_host_dir}:${code_docker_dir}" \
    ${volume_usb[@]+"${volume_usb[@]}"} \
    "-e" "GRADLE_USER_HOME=/gradle" \
    "-e" "GRADLE_OPTS=-Dorg.gradle.workers.max=$num_cpus" \
    ${use_maven_repo_flags[@]+"${use_maven_repo_flags[@]}"} \
    $(env | cut -f1 -d= | grep OBLIVIUM_ | sed 's/^OBLIVIUM_/-e OBLIVIUM_/') \
    "-w" "$code_docker_dir" \
)

function loadBuildImage() {
    if [ -z "${DOCKER_IMAGE_LOAD:-}" ] || [ "${DOCKER_IMAGE_LOAD}" == "1" ]; then
        docker load < $code_host_dir/build/containers/graalvm-build-docker-image.tar.gz
    fi
}

function runDocker() {
    image_name=$1
    docker run \
        ${docker_opts[@]+"${docker_opts[@]}"} \
        ${agent_home_dir_flags[@]+"${agent_home_dir_flags[@]}"} \
        ${OBLIVIUM_CONTAINER_REGISTRY_URL}/${image_name} \
        bash -c "$2"
}
