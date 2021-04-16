#!/usr/bin/env bash
set -euo pipefail
script_dir=$(dirname ${BASH_SOURCE[0]})
if [ -f ~/.oblivium_credentials.sh ]
then
  source ~/.oblivium_credentials.sh
fi
source ${script_dir}/ci_build_common.sh
# You can set this variable to mount the IDEs from the host
host_ide_dir=${HOST_IDE_DIR:-"${HOME}/.opt"}
idea_version=${IDEA_VERSION:-"IC-203.6682.168"}
idea_download_file=ideaIC-2020.3.1.tar.gz
idea_download_address=https://download-cf.jetbrains.com/idea/$idea_download_file
clion_version=${clion_version:-"2020.2.4"}
clion_download_file=CLion-2020.2.4.tar.gz
clion_download_address=https://download-cf.jetbrains.com/cpp/$clion_download_file

source ${script_dir}/devenv_envs.sh

container_name=$(echo "code${code_host_dir}" | sed -e 's/[^a-zA-Z0-9_.-]/_/g')
container_id=$(docker ps -aqf name=^/$container_name\$ || echo "")
docker_image_pull="${DOCKER_IMAGE_PULL:-0}"
if [[ -z ${container_id} ]]; then
  # We don't want to / can't log in to the local registry. This step is only useful for remote registries.
  if [ "$OBLIVIUM_CONTAINER_REGISTRY_URL" != "localhost:5000" ] && [ "$docker_image_pull" == "1" ]; then
    docker login $OBLIVIUM_CONTAINER_REGISTRY_URL -u $OBLIVIUM_CONTAINER_REGISTRY_USERNAME -p $OBLIVIUM_CONTAINER_REGISTRY_PASSWORD
    docker pull $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-build
  else
    DOCKER_IMAGE_AESMD_BUILD="${DOCKER_IMAGE_AESMD_BUILD:-0}" DOCKER_IMAGE_SAVE="${DOCKER_IMAGE_SAVE:-0}" ${script_dir}/ci_build_docker_images.sh
  fi

  env_display=""
  if [[ ! -z ${DISPLAY+x} ]]
  then
    xhost +
    if [ "$(uname)" == "Darwin" ]; then
      env_display="-e DISPLAY=host.docker.internal:0"
    else
      env_display="-e DISPLAY=${DISPLAY}"
    fi
  fi

  if [ ${CONCLAVE_DOCKER_IDE:-""} = "1" ]; then
    if [ ! -d ${host_ide_dir}/idea-$idea_version ]; then
      # remove any previous version of IntelliJ IDEA that might be dangling around
      rm -fr ${host_ide_dir}/idea-*
      echo "Downloading IntelliJ IDEA from ${idea_download_address}..."
      mkdir -p $host_ide_dir
      curl -SL -o ${host_ide_dir}/$idea_download_file $idea_download_address
      echo "ae00e8cfc9a41a772d3f7a7aba463a744ea832f8  ${host_ide_dir}/$idea_download_file" | shasum -c -
      tar -C $host_ide_dir -zxvf ${host_ide_dir}/$idea_download_file
      rm ${host_ide_dir}/$idea_download_file
    fi
    if [ ! -d ${host_ide_dir}/clion-$clion_version ]; then
      # remove any previous version of clion that might be dangling around
      rm -fr ${host_ide_dir}/clion-*
      echo "Downloading CLion from ${clion_download_address}..."
      mkdir -p $host_ide_dir
      curl -SL -o ${host_ide_dir}/$clion_download_file $clion_download_address
      tar -C $host_ide_dir -zxvf ${host_ide_dir}/$clion_download_file
      rm ${host_ide_dir}/$clion_download_file
    fi
  else
    echo "Set the environment variable 'CONCLAVE_DOCKER_IDE=1' to automatically download IntelliJ IDEA and CLion."
  fi
# Mount the IDEs from the host. The IDEs should be compatible with the container's OS.
# If the IDEs are not present, the devenv shell can still be used,
# as well as the the host's native IDEs, but the IDE launch scripts will fail.
# Since exiting the container doesn't stop it, you may need to stop it manually
# in order to remount the IDEs.
# Read our internal-docs/containers.md for more information on how to build,
# customize and use a locally built image.
# The IDEs can be downloaded at:
# curl -sSL -o /opt/clion.tar.gz https://download-cf.jetbrains.com/cpp/CLion-2018.1.2.tar.gz
# curl -sSL -o /opt/idea.tar.gz https://download-cf.jetbrains.com/idea/ideaIC-2019.3.2.tar.gz
ide_mount_flags=""
if [ -r ${host_ide_dir}/idea-$idea_version ]; then
  ide_mount_flags+="-v ${host_ide_dir}/idea-$idea_version/:/opt/idea-$idea_version/"
fi
if [ -r ${host_ide_dir}/clion-$clion_version ]; then
  ide_mount_flags+=" -v ${host_ide_dir}/clion-$clion_version/:/opt/clion-$clion_version/"
fi

  container_id=$(docker run \
       --name=$container_name \
       ${docker_opts[@]+"${docker_opts[@]}"} \
       --privileged \
       --add-host="$(hostname):${docker_ip}" \
       -v /tmp/.X11-unix:/tmp/.X11-unix \
       ${ide_mount_flags} \
       ${env_display} \
       -d \
       -it \
       $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-build \
       bash)

  # Set access to docker daemon socket
  if [ "$(uname)" == "Darwin" ]; then
    docker exec -u root $container_id chgrp $(id -g) /var/run/docker.sock
  else
    docker exec -it $@ -u root $container_id bash -c "groupadd -g ${docker_gid} docker_ext || true"
  fi

  # DCAP 1.41+ driver access permissions.
  if [ -e /dev/sgx_provision ]; then
    docker exec -u root $container_id chgrp $(id -g) /dev/sgx_provision
  fi

  # Add entry to container's hostname in /etc/hosts, if it's not there, due to different behaviour in macOS.
  docker exec -u root $container_id sh -c 'grep "\$\(hostname\)" /etc/hosts || printf "%s\t%s\n" $(ip address show docker0 2> /dev/null | sed -n "s/^.*inet \(addr:[ ]*\)*\([^ ]*\).*/\2/p" | cut -d/ -f1) $(hostname) >> /etc/hosts'

  # Let us read/write to the home directory.
  docker exec -u root $container_id chown $(id -u):$(id -g) /home
  if [[ ! -z ${cardreader_gid} ]]; then
    docker exec -it $@ -u root $container_id bash -c "groupadd -g ${cardreader_gid} cardreader_ext || true"
  fi

  # Start the docsite servers. They have hot-reload so editing the files in docs (for docs.conclave.net) or
  # internal-docs (not published on the web) will automatically cause any open browsers to refresh.
  docker exec -d $container_id bash ./scripts/serve-docsites.sh
fi
