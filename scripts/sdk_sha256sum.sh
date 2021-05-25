#!/usr/bin/env bash
set -xeuo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})

cd "${script_dir}"/../build/distributions/
sdk_zip=$(ls conclave-sdk-*.zip)

sha256sum "${sdk_zip}" > SHA256SUM