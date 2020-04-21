#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

# Hardware tests
# Teardown any aesmd container that might be left running, build and start the aesmd container.
# Run the tests and teardown the aesmd afterwards.
# The driver is expected to already be installed and loaded on the CI agent.
docker stop aesmd || true
docker rm aesmd || true
cd containers/aesmd/src/docker && docker build --no-cache -t localhost:5000/com.r3.sgx/aesmd .
docker run -d --rm --name aesmd ${SGX_HARDWARE_FLAGS} localhost:5000/com.r3.sgx/aesmd
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR/samples && \$GRADLE -PexcludeProjectModules=true -Psgx_mode=Debug test -i"
docker stop aesmd
docker rmi localhost:5000/com.r3.sgx/aesmd
