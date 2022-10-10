#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

runDocker $container_image_sdk_build "./gradlew buildDocs test publishAllPublicationsToBuildRepository -s -i -x shadowJar"
