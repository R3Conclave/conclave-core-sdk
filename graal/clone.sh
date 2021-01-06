#!/usr/bin/env bash
set -xeuo pipefail

graal_version=$1
filesystem_jar=$2

basedir=$(dirname $0)
rm -fr graal
git clone --depth 1 https://github.com/oracle/graal.git -b release/graal-vm/$graal_version
cd graal
patch -p1 -i $basedir/patches/graal_$graal_version.patch
jimfs_sha1=`sha1sum $filesystem_jar | cut -d' ' -f1`
sed -i "s/\"sha1\": \"5b311b772390a79700d1a0693d282cb88a19fb19\"/\"sha1\": \"$jimfs_sha1\"/" substratevm/mx.substratevm/suite.py
sed -i "s|filesystem.jar|$filesystem_jar|" substratevm/mx.substratevm/suite.py
