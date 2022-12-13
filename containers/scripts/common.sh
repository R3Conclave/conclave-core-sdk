#!/usr/bin/env bash
set -xeuo pipefail

###################################################################
# Helper functions
###################################################################
function getVersionValueFromVersionsFile() {
  versionName=$1
  grep "$versionName" versions.gradle | cut -d '=' -f2 | sed "s/[\' ]//g"
}

function getConclaveVersion() {
  getVersionValueFromVersionsFile conclave_version
}

function getConclaveGraalVersion() {
  getVersionValueFromVersionsFile conclave_graal_version
}

# Returns the most recent Git commit id
function getGitCommitId() {
  git rev-parse HEAD
}

function getJepVersion() {
  getVersionValueFromVersionsFile jep_version
}

# Returns 0 if the docker image exists. Otherwise, 1.
# Returning zero as true is strange but that is the convention with bash shell.
# N.B. The image might exist locally and not on the remote server
doesContainerImageExist() {
  docker manifest inspect $1 &> /dev/null
}

###################################################################
# Configuration
###################################################################
# Graal Configuration
conclave_graal_group_id="com/r3/conclave"
conclave_graal_version=$(getConclaveGraalVersion)
conclave_graal_artifact_id="graalvm"
