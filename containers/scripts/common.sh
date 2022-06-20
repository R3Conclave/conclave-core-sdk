###################################################################
# Helper functions
###################################################################
getConclaveVersion() {
  grep "conclave_version" versions.gradle | grep "conclave_version" | cut -d '=' -f2 | sed "s/[\' ]//g"
}

# Returns the most recent Git commit id
getGitCommitId() {
  git rev-parse HEAD
}