#!/usr/bin/env bash
set -xeuo pipefail
shopt -s extglob

script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

# Build the build-image and the enclave Release artifacts.
runDocker $container_image_sdk_build "./gradlew containers:sdk-build:buildImagePublish"
runDocker $container_image_sdk_build "cd samples && ./gradlew buildSignedEnclaveRelease -i"

mkdir FIRST && for so in samples/*/build/conclave/release/*.so; do
    mv $so FIRST
done
cd FIRST && sha256sum !(*.signed).so > SHA256SUM && cd -

# Clean the image completely.
runDocker $container_image_sdk_build "./gradlew clean && ./gradlew --stop"
runDocker $container_image_sdk_build "cd samples && ./gradlew clean && ./gradlew --stop"

# Change the name of the container's mount-point.
# Any lingering references to the mount-point inside the enclave
# will therefore change its SHA256 hash.
code_docker_dir="/code"

# Rebuild the build-image and enclave Release artifacts.
runDocker $container_image_sdk_build "./gradlew containers:sdk-build:buildImagePublish"
runDocker $container_image_sdk_build "cd samples && ./gradlew buildSignedEnclaveRelease -i"

mkdir SECOND && for so in samples/*/build/conclave/release/*.so; do
    mv $so SECOND
done
cd SECOND && sha256sum !(*.signed).so > SHA256SUM && cd -

# Final task: compare the two sets of hashes
diff -u FIRST/SHA256SUM SECOND/SHA256SUM
