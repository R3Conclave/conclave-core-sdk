#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Publishes the SDK into Nexus IQ for security and license analysis.
loadBuildImage

# Build the SDK and publish it into Nexus-IQ
runDocker com.r3.sgx/sgxjvm-build "./scripts/nexus-iq_publish.sh $1 $2"