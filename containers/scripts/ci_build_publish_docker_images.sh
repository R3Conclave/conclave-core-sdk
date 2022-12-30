#!/usr/bin/env bash
set -xeuo pipefail

###################################################################
## Read any arguments passed to the script
###################################################################
# Publishing the docker container images are turned off by default to allow people to run this script without getting an error.
# Most likely, no developer has permissions to publish to remote repository.
# In order to publish the docker container images, set the first argument to "publish". For instance, ./containers/scripts/ci_build_publish_docker_images.sh publish.
publish_images="${1:-}"

###################################################################
## Configuration
###################################################################
# The following variable contains the path to the containers/script directory
containers_script_dir=$(dirname ${BASH_SOURCE[0]})
script_dir=$containers_script_dir/../../scripts

source ${script_dir}/ci_build_common.sh
source ${containers_script_dir}/common.sh

commit_id=$(getGitCommitId)
jep_version=$(getJepVersion)
gramine_version=$(getGramineVersion)

# Downloads or copys Graal from a local directory. This is required for building the sdk build.
downloadOrCopyGraal() {
  downloads_dir="root/downloads"
  conclave_graal_tar_file="${downloads_dir}/graalvm.tar.gz"
  conclave_graal_artifact_path=$conclave_graal_group_id/$conclave_graal_artifact_id/$conclave_graal_version/$conclave_graal_artifact_id-$conclave_graal_version.tar.gz

  # Delete all files from the directory to avoid issues
  rm -rf $downloads_dir/*

  # If you are upgrading Graal version consider setting the environment variable GRAAL_DIR to the local directory where Graal was built.
  # For instance, GRAAL_DIR=../../../../graalvm.tar.gz. Keep in mind the working directory when setting the environment variable.
  if [[ -z "${GRAAL_DIR:-}" ]]; then
    url="https://software.r3.com/artifactory/conclave-maven/${conclave_graal_artifact_path}"

    echo "Pulling Graal from $url..."
    echo "For testing purposes point the env. var. GRAAL_DIR to the local directory where Graal was built. For instance, GRAAL_DIR=../../../../graal/build/distributions/graal-sdk.tar.gz. Keep in mind the working directory when setting the environment variable."
    echo "Working directory:" $(pwd)
    curl -SLf -o ${conclave_graal_tar_file} --create-dirs $url
  else
    # Ensure the directory exists
    mkdir -p $downloads_dir

    # This copy is required to allow docker to access the file
    echo "Working directory:" $(pwd)
    cp $GRAAL_DIR $conclave_graal_tar_file
  fi
}

# Builds sdk-build docker image
buildContainerSDKBuild() {
  pushd "${code_host_dir}/containers/sdk-build/src/docker"

  # Skip the step that downloads/copies Graal if Graal does not changes between runs
  if [ -z "${GRAAL_SKIP_DOWNLOAD_OR_COPY_STEP:-}" ]; then
    echo "Skip Graal download/copy by setting env. var. GRAAL_SKIP_DOWNLOAD_OR_COPY_STEP if Graal does not changes between runs."
    downloadOrCopyGraal
  fi

  docker build -t $container_image_sdk_build --build-arg commit_id=$commit_id --build-arg jep_version="$jep_version" .
  popd
}

# Builds conclave-build docker image
buildContainerConclaveBuild() {
  pushd "${code_host_dir}/containers/conclave-build/"
  docker build -t $container_image_conclave_build --build-arg commit_id=$commit_id --build-arg gramine_version="$gramine_version" .
  popd
}

# Builds conclave-run docker image
buildContainerConclaveRun() {
  pushd "${code_host_dir}/containers/conclave-run/"
  docker build -t $container_image_conclave_run --build-arg commit_id=$commit_id --build-arg gramine_version="$gramine_version" .
  popd
}

# Builds integration-tests-build docker image (N.B.: Must be built after the conclave-build.)
buildContainerIntegrationTestsBuild() {
  pushd "${code_host_dir}/containers/integration-tests-build"
  docker build -t $container_image_integration_tests_build --build-arg container_base_image=$container_image_conclave_build --build-arg commit_id=$commit_id --build-arg jep_version="$jep_version" .
  popd
}

# Builds AESMD docker image (this is used by Gramine hardware integration tests)
buildContainerAESMD() {
  pushd "${code_host_dir}/containers/aesmd/"
  docker build -t "$container_image_aesmd" --build-arg commit_id="$commit_id" .
  popd
}

# Prints a message and builds the container image based on the function passed as a second argument
buildContainer() {
    echo "Building docker image container $1..."
    # Run the function passed in as the second argument
    $2
}

# Check if the a docker container image exists. If the image does not exist then it is created and published
# The function takes as first argument the docker container image name and the function that generates the docker image
# if required
buildAndPublishContainerIfItDoesNotExist() {
  echo "Checking if container $1 exists..."
  if doesContainerImageExist $1; then
    echo "Container $1 has been found. Docker container image will not be built"
  else
    echo "Container $1 not found."
    buildContainer $1 $2
    # Only publish the image if requested. Not all users are authorized to publish to the repository.
    if [ "$publish_images" == "publish" ]; then
      docker login conclave-docker-dev.software.r3.com -u $CONCLAVE_ARTIFACTORY_USERNAME -p $CONCLAVE_ARTIFACTORY_PASSWORD
      docker push $1
    else
      echo "Container image will not be published. To publish the container image type ./ci_build_publish_docker_images.sh publish. You must be authorized user to publish to the repository"
    fi
  fi
}

##################################################################################
## Build and publish
##################################################################################
if [ -z "${DOCKER_IMAGE_AESMD_BUILD:-}" ] || [ "${DOCKER_IMAGE_AESMD_BUILD}" == "1" ]; then
  buildAndPublishContainerIfItDoesNotExist "$container_image_aesmd" buildContainerAESMD
fi
if [ -z "${DOCKER_IMAGE_CONCLAVE_BUILD:-}" ] || [ "${DOCKER_IMAGE_CONCLAVE_BUILD}" == "1" ]; then
  buildAndPublishContainerIfItDoesNotExist $container_image_conclave_build buildContainerConclaveBuild
  buildAndPublishContainerIfItDoesNotExist $container_image_conclave_run buildContainerConclaveRun
fi
if [ -z "${DOCKER_IMAGE_CONCLAVE_BUILD_INTEGRATION_TESTS:-}" ] || [ "${DOCKER_IMAGE_CONCLAVE_BUILD_INTEGRATION_TESTS}" == "1" ]; then
  buildAndPublishContainerIfItDoesNotExist $container_image_integration_tests_build buildContainerIntegrationTestsBuild
fi
buildAndPublishContainerIfItDoesNotExist $container_image_sdk_build buildContainerSDKBuild
