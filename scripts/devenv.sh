#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
# You can set this variable to mount the IDEs from the host
HOST_IDE_DIR=${HOST_IDE_DIR:-"${HOME}/.opt"}
IDEA_VERSION=${IDEA_VERSION:-"IC-203.6682.168"}
IDEA_DOWNLOAD_FILE=ideaIC-2020.3.1.tar.gz
IDEA_DOWNLOAD_ADDRESS=https://download-cf.jetbrains.com/idea/$IDEA_DOWNLOAD_FILE
CLION_VERSION=${CLION_VERSION:-"2020.2.4"}
CLION_DOWNLOAD_FILE=CLion-2020.2.4.tar.gz
CLION_DOWNLOAD_ADDRESS=https://download-cf.jetbrains.com/cpp/$CLION_DOWNLOAD_FILE

if [ -f ~/.oblivium_credentials.sh ]
then
  source ~/.oblivium_credentials.sh
fi

source ${SCRIPT_DIR}/devenv_envs.sh

# Docker environment
if [ "$(uname)" == "Darwin" ]; then
  docker_ip="192.168.65.2"
else
  if [[ $(uname -r) == *microsoft* ]]; then
    docker_ip="172.17.0.2"
  else
    docker_ip=$(ip address show docker0 2> /dev/null | sed -n 's/^.*inet \(addr:[ ]*\)*\([^ ]*\).*/\2/p' | cut -d/ -f1)
    if [ -z "$docker_ip" ]; then
      docker_ip="172.17.0.2"
    fi
  fi
fi

mkdir -p ${HOME}/.gradle
mkdir -p ${HOME}/.m2
mkdir -p ${HOME}/.mx
mkdir -p ${HOME}/.container

CODE_HOST_DIR=$PWD
export CONTAINER_NAME=$(echo "code${CODE_HOST_DIR}" | sed -e 's/[^a-zA-Z0-9_.-]/_/g')
CODE_DOCKER_DIR=$CODE_HOST_DIR
export CONTAINER_ID=$(docker ps -aqf name=^/$CONTAINER_NAME\$ || echo "")
if [[ -z ${CONTAINER_ID} ]]; then
  # We don't want to / can't log in to the local registry. This step is only useful for remote registries.
  if [[ "$OBLIVIUM_CONTAINER_REGISTRY_URL" != "localhost:5000" ]]; then
    docker login $OBLIVIUM_CONTAINER_REGISTRY_URL -u $OBLIVIUM_CONTAINER_REGISTRY_USERNAME -p $OBLIVIUM_CONTAINER_REGISTRY_PASSWORD
    docker pull $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-devenv
  fi

  ENV_DISPLAY=""
  if [[ ! -z ${DISPLAY+x} ]]
  then
    xhost +
    if [ "$(uname)" == "Darwin" ]; then
      ENV_DISPLAY="-e DISPLAY=host.docker.internal:0"
    else
      ENV_DISPLAY="-e DISPLAY=${DISPLAY}"
    fi
  fi
  if [[ -d /dev/bus/usb ]]

  then
    VOLUME_USB="-v /dev/bus/usb:/dev/bus/usb"
  else
    VOLUME_USB=""
  fi

  if [ "$(uname)" == "Darwin" ]; then
    docker_group_add=""
    cardreader_gid=""
  else
    docker_gid=$(cut -d: -f3 < <(getent group docker))
    if [[ "$docker_gid" == "" ]]; then
      echo "You don't appear to have a docker UNIX group configured. This script requires one."
      echo
      echo "Follow the post-install instructions at https://docs.docker.com/install/linux/linux-postinstall/"
      echo "to finish the Docker setup process."
      exit 1
    fi
    docker_group_add="--group-add ${docker_gid}"
    cardreader_gid=$(cut -d: -f3 < <(getent group cardreader) || echo "")
  fi

  if [[ ! -z ${cardreader_gid} ]]
  then
    GROUP_CARDREADER="--group-add ${cardreader_gid}"
  else
    GROUP_CARDREADER=""
  fi

  if [ "$(uname)" == "Darwin" ]; then
    network_cmd="-p 8000:8000 -p 8001:8001"
    num_cpus=$( sysctl -n hw.ncpu )
  else
    network_cmd="--network host"
    num_cpus=$( nproc )
  fi

  # If running on a Linux host with SGX properly installed and configured,
  # tunnel through the SGX driver and AES daemon socket. This means you can
  # still run the devenv on a non-SGX host or a Mac without it breaking.
  SGX_HARDWARE_FLAGS=""
  if [ -e /dev/isgx ] && [ -d /var/run/aesmd ]; then
      SGX_HARDWARE_FLAGS="--device=/dev/isgx -v /var/run/aesmd:/var/run/aesmd"
  elif [ -e /dev/sgx/enclave ] && [ -d /var/run/aesmd ]; then
      # dcap driver
      SGX_HARDWARE_FLAGS="--device=/dev/sgx/enclave --device=/dev/sgx/provision -v /var/run/aesmd:/var/run/aesmd"
  fi

  if [ ${CONCLAVE_DOCKER_IDE:-""} = "1" ]; then
    if [ ! -d ${HOST_IDE_DIR}/idea-$IDEA_VERSION ]; then
      # remove any previous version of IntelliJ IDEA that might be dangling around
      rm -fr ${HOST_IDE_DIR}/idea-*
      echo "Downloading IntelliJ IDEA from ${IDEA_DOWNLOAD_ADDRESS}..."
      mkdir -p $HOST_IDE_DIR
      curl -SL -o ${HOST_IDE_DIR}/$IDEA_DOWNLOAD_FILE $IDEA_DOWNLOAD_ADDRESS
      echo "ae00e8cfc9a41a772d3f7a7aba463a744ea832f8  ${HOST_IDE_DIR}/$IDEA_DOWNLOAD_FILE" | shasum -c -
      tar -C $HOST_IDE_DIR -zxvf ${HOST_IDE_DIR}/$IDEA_DOWNLOAD_FILE
      rm ${HOST_IDE_DIR}/$IDEA_DOWNLOAD_FILE
    fi
    if [ ! -d ${HOST_IDE_DIR}/clion-$CLION_VERSION ]; then
      # remove any previous version of clion that might be dangling around
      rm -fr ${HOST_IDE_DIR}/clion-*
      echo "Downloading CLion from ${CLION_DOWNLOAD_ADDRESS}..."
      mkdir -p $HOST_IDE_DIR
      curl -SL -o ${HOST_IDE_DIR}/$CLION_DOWNLOAD_FILE $CLION_DOWNLOAD_ADDRESS
      tar -C $HOST_IDE_DIR -zxvf ${HOST_IDE_DIR}/$CLION_DOWNLOAD_FILE
      rm ${HOST_IDE_DIR}/$CLION_DOWNLOAD_FILE
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
# customize and use a locally built devenv image.
# The IDEs can be downloaded at:
# curl -sSL -o /opt/clion.tar.gz https://download-cf.jetbrains.com/cpp/CLion-2018.1.2.tar.gz
# curl -sSL -o /opt/idea.tar.gz https://download-cf.jetbrains.com/idea/ideaIC-2019.3.2.tar.gz
IDE_MOUNT_FLAGS=""
if [ -r ${HOST_IDE_DIR}/idea-$IDEA_VERSION ]; then
  IDE_MOUNT_FLAGS+="-v ${HOST_IDE_DIR}/idea-$IDEA_VERSION/:/opt/idea-$IDEA_VERSION/"
fi
if [ -r ${HOST_IDE_DIR}/clion-$CLION_VERSION ]; then
  IDE_MOUNT_FLAGS+=" -v ${HOST_IDE_DIR}/clion-$CLION_VERSION/:/opt/clion-$CLION_VERSION/"
fi

  CONTAINER_ID=$(docker run \
       --name=$CONTAINER_NAME \
       --privileged \
       $network_cmd \
       --label "sgxjvm" \
       --ulimit core=256000000 \
       --add-host="$(hostname):${docker_ip}" \
       --rm \
       -u $(id -u):$(id -g) \
       ${GROUP_CARDREADER} \
       ${docker_group_add} \
       -v ${HOME}/.gradle:/gradle \
       -v ${HOME}/.m2:/home/.m2 \
       -v ${HOME}/.mx:/home/.mx \
       -v ${HOME}/.container:/home \
       -v $CODE_HOST_DIR:$CODE_DOCKER_DIR \
       -v /var/run/docker.sock:/var/run/docker.sock \
       -v /tmp/.X11-unix:/tmp/.X11-unix \
       ${SGX_HARDWARE_FLAGS} \
       ${IDE_MOUNT_FLAGS} \
       ${VOLUME_USB} \
       -e HOME=/home \
       ${ENV_DISPLAY} \
       -e GRADLE_USER_HOME=/gradle \
       -e GRADLE_OPTS="-Dorg.gradle.workers.max=$num_cpus" \
       $(env | cut -f1 -d= | grep OBLIVIUM_ | sed 's/^OBLIVIUM_/-e OBLIVIUM_/') \
       -d \
       -it \
       -w $CODE_DOCKER_DIR \
       $OBLIVIUM_CONTAINER_REGISTRY_URL/com.r3.sgx/sgxjvm-devenv \
       bash)

  # Set access to docker daemon socket
  if [ "$(uname)" == "Darwin" ]; then
    docker exec -u root $CONTAINER_ID chgrp $(id -g) /var/run/docker.sock
  else
    docker exec -it $@ -u root $CONTAINER_ID bash -c "groupadd -g ${docker_gid} docker_ext || true"
  fi
  # Add entry to container's hostname in /etc/hosts, if it's not there, due to different behaviour in macOS.
  docker exec -u root $CONTAINER_ID sh -c 'grep "\$\(hostname\)" /etc/hosts || printf "%s\t%s\n" $(ip address show docker0 2> /dev/null | sed -n "s/^.*inet \(addr:[ ]*\)*\([^ ]*\).*/\2/p" | cut -d/ -f1) $(hostname) >> /etc/hosts'

  # Let us read/write to the home directory.
  docker exec -u root $CONTAINER_ID chown $(id -u):$(id -g) /home
  if [[ ! -z ${cardreader_gid} ]]; then
    docker exec -it $@ -u root $CONTAINER_ID bash -c "groupadd -g ${cardreader_gid} cardreader_ext || true"
  fi

  # Start the docsite servers. They have hot-reload so editing the files in docs (for docs.conclave.net) or
  # internal-docs (not published on the web) will automatically cause any open browsers to refresh.
  docker exec -d $CONTAINER_ID bash ./scripts/serve-docsites.sh
fi
