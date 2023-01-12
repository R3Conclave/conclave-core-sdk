#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

if [[ $# -ne 2 ]]; then
    echo "Wrong number of parameters, enter enclave mode and runtime type." >&2
    exit 1
fi

# Set parameters to lowercase
enclaveMode=${1,,}
runtimeType=${2,,}

# Check the entered enclave mode and runtime type
[[ "mock simulation debug release" =~ (^|[[:space:]])$enclaveMode($|[[:space:]]) ]] && echo $enclaveMode || ( echo "Wrong enclave mode entered: $enclaveMode."; exit 1 )
[[ "graalvm gramine" =~ (^|[[:space:]])$runtimeType($|[[:space:]]) ]] && echo $runtimeType || ( echo "Wrong runtime type entered: $runtimeType."; exit 1 )

if [ "$enclaveMode" != "simulation" ] && [ "$runtimeType" == "gramine" ]; then
    # Hardware tests for Gramine need the AESM service running.
    # Here we teardown any AESMD container that might be left running, build and start the AESMD container.
    docker stop aesmd || true
    docker run -d --rm --name aesmd ${sgx_hardware_flags[@]+"${sgx_hardware_flags[@]}"} "$container_image_aesmd"
fi

# Gramine needs a custom seccomp profile to work. https://github.com/gramineproject/gramine/issues/164#issuecomment-949349475
runDocker "$container_image_integration_tests_build" "cd integration-tests && ./gradlew -PenclaveMode=$enclaveMode -PruntimeType=$runtimeType test -s -i" "--security-opt seccomp=$script_dir/gramine-seccomp.json"

if [ "$enclaveMode" != "simulation" ] && [ "$runtimeType" == "gramine" ]; then
    # Teardown AESM container
    docker stop aesmd || true
fi
