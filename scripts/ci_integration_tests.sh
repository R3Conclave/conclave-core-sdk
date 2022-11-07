#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

enclaveMode=$1
runtimeType=$2

# Gramine needs a custom seccomp profile to work. https://github.com/gramineproject/gramine/issues/164#issuecomment-949349475
runDocker $container_image_integration_tests_build "cd integration-tests && ./gradlew -PenclaveMode=$enclaveMode -PruntimeType=$runtimeType test -s -i" "--security-opt seccomp=$script_dir/gramine-seccomp.json"
