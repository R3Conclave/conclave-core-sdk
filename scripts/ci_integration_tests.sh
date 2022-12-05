#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

enclaveMode=$1
runtimeType=$2

if [ "$enclaveMode" != "Simulation" ] && [ "$runtimeType" == "Gramine" ]; then
    # Hardware tests for Gramine need AESMD service running.
    # Here we teardown any AESMD container that might be left running, build and start the AESMD container.
    docker stop aesmd || true
    docker run -d --rm --name aesmd ${sgx_hardware_flags[@]+"${sgx_hardware_flags[@]}"} "$container_image_aesmd"
fi

# Gramine needs a custom seccomp profile to work. https://github.com/gramineproject/gramine/issues/164#issuecomment-949349475
runDocker "$container_image_integration_tests_build" "cd integration-tests && ./gradlew -PenclaveMode=$enclaveMode -PruntimeType=$runtimeType test -s -i" "--security-opt seccomp=$script_dir/gramine-seccomp.json"

if [ "$enclaveMode" != "Simulation" ] && [ "$runtimeType" == "Gramine" ]; then
    # Teardown AESM container
    docker stop aesmd || true
fi
