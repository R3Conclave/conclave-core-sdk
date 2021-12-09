#!/usr/bin/env bash
set -xeuo pipefail

graal_version=$1

basedir=$(dirname "$(realpath "$0")")
rm -fr graal
git clone --depth 1 https://github.com/oracle/graal.git -b "release/graal-vm/$graal_version"
cd graal
patch -p1 -i "$basedir/patches/graal.patch" --no-backup-if-mismatch
