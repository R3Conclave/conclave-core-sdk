#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

# Publish. All testing should be done before this, i.e. running ci_build.sh
runDocker com.r3.sgx/sgxjvm-build "cd $CODE_DOCKER_DIR && \$GRADLE publish -i && cd $CODE_DOCKER_DIR/sgx-jvm-plugin && \$GRADLE publish -i"
