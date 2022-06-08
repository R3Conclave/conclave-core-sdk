#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

script_dir_no_symlink_resolve=$(dirname $(realpath -s $0))

# TODO investigate making the latex pdf generation work

# Build build container
runDocker com.r3.sgx/sdk-build "cd ${script_dir}/.. && ./gradlew containers:sdk-build:buildImagePublish"

# Build docsite
runDocker com.r3.sgx/sdk-build "cd $script_dir_no_symlink_resolve/../../docs && ./install-docsite-requirements.sh && ./make-docsite.sh"
