#!/usr/bin/env bash
set -euo pipefail

# Ensure the code is run at the root directory of the project
code_host_dir=$(dirname $(dirname ${BASH_SOURCE[0]}))
pushd ${code_host_dir} > /dev/null # Do not print the stack to standard output otherwise it will break the plugin-enclave-gradle function getDockerContainerTag

source ./containers/scripts/common.sh

###################################################################
# Helper functions
###################################################################
function getConclaveGraalSha512Sum() {
  # Set the url which points to the file that contains the sha512sum of the Conclave Graal artifact
  conclave_graal_version=$(grep -w "conclave_graal_version =" ./versions.gradle | cut -d '=' -f 2 | sed "s/[ ']//g")
  artifact_path=$conclave_graal_group_id/$conclave_graal_artifact_id/$conclave_graal_version/$conclave_graal_artifact_id-$conclave_graal_version.tar.gz.sha512
  url="https://software.r3.com/artifactory/conclave-maven/${artifact_path}"

  # Fetch the Sha512 of the Conclave Graal artifact
  conclave_graal_sha512sum=$(curl -SLf $url)

  # Return the result
  echo $conclave_graal_sha512sum
}

function genDockerImageTag() {
  # Generate the docker image tag based on the contents inside the containers module
  # Please be sure that any script that might change the final docker container image
  # is inside the folder containers/scripts. Otherwise, the tag generated won't be
  # correct and you run the risk of overwriting existing docker images that are used
  # by older release branches. Keep in mind that temporary or build directories should be excluded
  # The following code generates the hash based on the contents of a directory and the version of graal used.
  # This hash takes into account the contents of each file inside the directory and subdirectories
  # The cut command removes the dash at the end.
  # All subdirectories with name build and download and hidden files are excluded. Please be sure that any file
  # that is not tracked by git should not be included in this hash.
  # In order to allow ci_build_publish_docker_images to detect automatically the new version of graal, the hash generated
  # must include the conclave_graal sha512sum as well.
  containers_dir_hash=$(find ./containers \( ! -regex '.*/\..*\|.*/build/.*\|.*/downloads/.*' \) -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -d ' ' -f1)
  docker_image_tag=$(echo $containers_dir_hash-$(getConclaveGraalSha512Sum) | sha256sum | cut -d ' ' -f1)
  # Return the result
  echo ${docker_image_tag}
}

###################################################################
# Run the function which generates the docker image tag. The result is printed to the standard output.
# N.B.: The output must be printed to the standard output because it is the only way for Gradle to capture the result without resorting to temporary files or environment variables
###################################################################
genDockerImageTag

# Return the original directory
popd > /dev/null # Do not print the stack to the standard output otherwise it will break the plugin-enclave-gradle function getDockerContainerTag
