#!/usr/bin/env bash
set -xeuo pipefail

version=$1
basedir=$(dirname $0)
rm -fr graal
git clone --depth 1 https://github.com/oracle/graal.git -b release/graal-vm/$version
cd graal
patch -p1 -i $basedir/patches/graal_20.2.patch
