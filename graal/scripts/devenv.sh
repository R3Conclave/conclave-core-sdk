#!/usr/bin/env bash
set -euo pipefail
script_dir=$(dirname ${BASH_SOURCE[0]})
if [ -f ~/.oblivium_credentials.sh ]
then
  source ~/.oblivium_credentials.sh
fi

source ${script_dir}/../../scripts/devenv_envs.sh
source ${script_dir}/ci_build_common.sh

container_name=$(echo "code${code_host_dir}" | sed -e 's/[^a-zA-Z0-9_.-]/_/g')
container_id=$(docker ps -aqf name=^/$container_name\$ || echo "")
docker_image_pull="${DOCKER_IMAGE_PULL:-0}"
if [[ -z ${container_id} ]]; then
  # We don't want to / can't log in to the local registry. This step is only useful for remote registries.
  if [ "$OBLIVIUM_CONTAINER_REGISTRY_URL" != "localhost:5000" ] && [ "$docker_image_pull" == "1" ]; then
    docker login $OBLIVIUM_CONTAINER_REGISTRY_URL -u $OBLIVIUM_CONTAINER_REGISTRY_USERNAME -p $OBLIVIUM_CONTAINER_REGISTRY_PASSWORD
    docker pull $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-build
  else
    DOCKER_IMAGE_SAVE="${DOCKER_IMAGE_SAVE:-0}" ${script_dir}/ci_build_docker_images.sh
  fi


  container_id=$(docker run \
       --name=$container_name \
       ${docker_opts[@]+"${docker_opts[@]}"} \
       --privileged \
       --add-host="$(hostname):${docker_ip}" \
       -v /tmp/.X11-unix:/tmp/.X11-unix \
       -d \
       -it \
       $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/graalvm-build \
       bash)

  # Set access to docker daemon socket
  if [ "$(uname)" == "Darwin" ]; then
    docker exec -u root $container_id chgrp $(id -g) /var/run/docker.sock
  else
    docker exec -it $@ -u root $container_id bash -c "groupadd -g ${docker_gid} docker_ext || true"
  fi

  # Add entry to container's hostname in /etc/hosts, if it's not there, due to different behaviour in macOS.
  docker exec -u root $container_id sh -c 'grep "\$\(hostname\)" /etc/hosts || printf "%s\t%s\n" $(ip address show docker0 2> /dev/null | sed -n "s/^.*inet \(addr:[ ]*\)*\([^ ]*\).*/\2/p" | cut -d/ -f1) $(hostname) >> /etc/hosts'

  # Let us read/write to the home directory.
  docker exec -u root $container_id chown $(id -u):$(id -g) /home
  if [[ ! -z ${cardreader_gid} ]]; then
    docker exec -it $@ -u root $container_id bash -c "groupadd -g ${cardreader_gid} cardreader_ext || true"
  fi
fi
