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
# Helper functions
###################################################################

# Returns the most recent Git commit id
getGitCommitId() {
  git rev-parse HEAD
}

###################################################################
## Configuration
###################################################################
script_dir=$(dirname ${BASH_SOURCE[0]})
source ${script_dir}/ci_build_common.sh

# Git commit id
commit_id=$(getGitCommitId)

###################################################################
# Functions
###################################################################
# Returns 0 if the docker image exists. Otherwise, 1.
# Returning zero as true is strange but that is the convention with bash shell.
# N.B. The image might exist locally and not on the remote server
doesContainerImageExist() {
  docker manifest inspect $1 &> /dev/null
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
      publishContainer $1
    else
      echo "Container image will not be published. To publish the container image type ./ci_build_publish_docker_images.sh publish. You must be authorized user to publish to the repository"
    fi
  fi
}


# Prints a message and builds the container image based on the function passed as a second argument
buildContainer() {
    echo "Building docker image container $1..."

    # Copy the setup-users-sh script. The file is being copied to reduce duplication
    root_script_dir="${code_host_dir}/containers/graalvm-build/docker/root/scripts"
    # Ensure the directory exists
    mkdir -p ${root_script_dir}
    # Copy the file
    cp "${code_host_dir}/../containers/sdk-build/src/docker/root/scripts/setup-users.sh" "${root_script_dir}/setup-users.sh"

    # Build the docker image based on the Dockerfile and save it to a file so it can be loaded when required
    # This will reduced the time required to run a container in TeamCity because the docker image is built once
    # and it can be loaded by the different subprojects such as "SDK Unit Tests and build", "SDK Integration Tests (Debug)",...
    build_dir="${code_host_dir}/build"
    dockerfile_dir="${code_host_dir}/containers/graalvm-build/docker"

    pushd "$dockerfile_dir"
    docker build -t "$1" --build-arg commit_id=$commit_id .
    popd
}

publishContainer() {
    docker push $1
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
    buildContainer $1
    # Only publish the image if requested. Not all users are authorized to publish to the repository.
    if [ "$publish_images" == "publish" ]; then
      publishContainer $1
    else
      echo "Container image will not be published. To publish the container image type ./ci_build_publish_docker_images.sh publish. You must be authorized user to publish to the repository"
    fi
  fi
}

##################################################################################
## Build and publish
##################################################################################
buildAndPublishContainerIfItDoesNotExist $container_image_graalvm_build