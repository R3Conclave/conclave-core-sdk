#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Publish. All testing should be done before this, i.e. running ci_build.sh
runDocker $container_image_sdk_build "" "./gradlew publish -s -i"
