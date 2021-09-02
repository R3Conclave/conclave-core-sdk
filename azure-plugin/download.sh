#!/usr/bin/env bash

set -e

if [[ -z "$1" ]]; then
    echo "Usage: download.sh <client-version>"
    exit 1
else
    CLIENT_VERSION=$1
fi

wget "https://packages.microsoft.com/ubuntu/18.04/prod/pool/main/a/az-dcap-client/az-dcap-client_${CLIENT_VERSION}_amd64.deb"
ar x "az-dcap-client_${CLIENT_VERSION}_amd64.deb" data.tar.xz
tar xvJf data.tar.xz --transform='s/.*\///' ./usr/lib/libdcap_quoteprov.so
rm "az-dcap-client_${CLIENT_VERSION}_amd64.deb" data.tar.xz
