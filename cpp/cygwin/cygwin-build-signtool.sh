#!/usr/bin/env bash

set -xeuo pipefail

dest=$(pwd)
temp=$(mktemp -d)

cd "$temp"
git clone -b sgx_2.13 --depth 1 https://github.com/intel/linux-sgx.git
cd linux-sgx
# For some reason, Cygwin's git is not "happy" with a patch being applied from an external folder.
# So we copy it locally...
cp "$dest/linux-sgx.cygwin.patch" .
# ...And apply the patch!
git apply -- linux-sgx.cygwin.patch
cd sdk/sign_tool/SignTool
make
mv sgx_sign.exe "$dest/"

rm -fr "$temp"