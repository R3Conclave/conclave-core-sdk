#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

enclaveMode=$1
runtimeType=$2

runDocker $container_image_integration_tests_build "cd integration-tests && ./gradlew -PenclaveMode=$enclaveMode -PruntimeType=$runtimeType test -s -i"
