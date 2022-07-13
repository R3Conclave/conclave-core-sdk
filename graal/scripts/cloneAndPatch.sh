#!/usr/bin/env bash
set -xeuo pipefail

basedir=$(dirname "$(realpath "$0")")
graal_version=$1
major_minor_graal_version=$(cut -d '.' -f 1,2 <<< $graal_version)
graal_repo="https://github.com/oracle/graal.git"
graal_branch="release/graal-vm/$major_minor_graal_version"
graal_patch="$basedir/../patches/graal.patch"
graal_dir="graal"

# Clean all directories to avoid issues
rm -fr "$graal_dir"

# Download Graal
git clone --depth 1 "$graal_repo" -b "$graal_branch"

# Apply the patch to Graal
cd "$graal_dir"
patch -p1 -i "$graal_patch" --no-backup-if-mismatch
