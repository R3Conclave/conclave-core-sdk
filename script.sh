#!/usr/bin/env bash
set -xeuo pipefail
source scripts/ci_build_common.sh
runDocker $container_image_sdk_build "./gradlew publishAllPublicationsToBuildRepository -s -i"
