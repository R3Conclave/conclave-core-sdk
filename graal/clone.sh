#!/usr/bin/env bash
set -xeuo pipefail

version=$1

mkdir -p build
cd build
rm -fr graal
git clone --depth 1 https://github.com/oracle/graal.git -b release/graal-vm/$version
cd graal
patch -p1 -i ../../patches/graal_20.1.patch
