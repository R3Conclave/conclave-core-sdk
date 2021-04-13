#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source $script_dir/devenv.sh

docker exec ${container_id} /opt/clion-$clion_version/bin/clion.sh
