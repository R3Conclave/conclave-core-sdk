#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Build the SDK and publish it into Nexus-IQ
runDocker $container_image_sdk_build "" "./scripts/nexus-iq_publish.sh $1 $2 $3"