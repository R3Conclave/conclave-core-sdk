#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

runDocker $container_image_conclave_build "./test-conclave-init.sh"
