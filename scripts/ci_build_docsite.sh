#!/usr/bin/env bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

SCRIPT_DIR_NO_SYMLINK_RESOLVE=$(dirname $(realpath -s $0))

# TODO investigate making the latex pdf generation work

# Build build container
runDocker com.r3.sgx/sgxjvm-build "cd ${SCRIPT_DIR}/.. && \$GRADLE containers:sgxjvm-build:buildImagePublish"

# Build docsite
runDocker com.r3.sgx/sgxjvm-build "cd $SCRIPT_DIR_NO_SYMLINK_RESOLVE/../../docs && ./install-docsite-requirements.sh && ./make-docsite.sh"
