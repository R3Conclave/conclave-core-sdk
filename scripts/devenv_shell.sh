#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})

source $SCRIPT_DIR/devenv.sh

docker exec -it $@ ${CONTAINER_ID} bash
