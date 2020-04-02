#!/usr/bin/env bash
set -xeuo pipefail
shopt -s extglob

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
source ${SCRIPT_DIR}/ci_build_common.sh

# Kill any lingering Gradle workers
ps auxwww | grep Gradle | grep -v grep | awk '{ print $2; }' | xargs -r kill || true

DOCKER_IMAGE=com.r3.sgx/sgxjvm-build

# Login and pull the current build image
docker login $OBLIVIUM_CONTAINER_REGISTRY_URL -u $OBLIVIUM_CONTAINER_REGISTRY_USERNAME -p $OBLIVIUM_CONTAINER_REGISTRY_PASSWORD
docker pull $OBLIVIUM_CONTAINER_REGISTRY_URL/$DOCKER_IMAGE

# Build the build-image and the enclave Release artifacts.
runDocker $DOCKER_IMAGE "cd $CODE_DOCKER_DIR && \$GRADLE containers:sgxjvm-build:buildImagePublish"
runDocker $DOCKER_IMAGE "cd $CODE_DOCKER_DIR/samples && \$GRADLE buildSignedEnclaveRelease -i"

mkdir FIRST && for SO in samples/*/build/enclave/Release/*.so; do
    mv $SO FIRST
done
cd FIRST && sha256sum !(*.signed).so > SHA256SUM && cd -

# Clean the image completely.
runDocker $DOCKER_IMAGE "cd $CODE_DOCKER_DIR && \$GRADLE clean && \$GRADLE --stop"
runDocker $DOCKER_IMAGE "cd $CODE_DOCKER_DIR/samples && \$GRADLE clean && \$GRADLE --stop"

# Change the name of the container's mount-point.
# Any lingering references to the mount-point inside the enclave
# will therefore change its SHA256 hash.
export CODE_DOCKER_DIR="/code"

# Rebuild the build-image and enclave Release artifacts.
runDocker $DOCKER_IMAGE "cd $CODE_DOCKER_DIR && \$GRADLE containers:sgxjvm-build:buildImagePublish"
runDocker $DOCKER_IMAGE "cd $CODE_DOCKER_DIR/samples && \$GRADLE buildSignedEnclaveRelease -i"

mkdir SECOND && for SO in samples/*/build/enclave/Release/*.so; do
    mv $SO SECOND
done
cd SECOND && sha256sum !(*.signed).so > SHA256SUM && cd -

# Final task: compare the two sets of hashes
diff -u FIRST/SHA256SUM SECOND/SHA256SUM
