#!/usr/bin/env bash

# (Debian compatible)
# Install GDB from GNU's ftp. 
# It will get the necessary packages, download the GDB source, compile and install it.

set -euo pipefail
GDB_DEFAULT_VERSION="gdb-9.2"
if [ $# -ne 0 ]; then
    if [ "$1" == "--help" ]; then
        echo "Install GDB from GNU's ftp in Debian systems."
        echo "If no parameter has been passed, it defaults to version ${GDB_DEFAULT_VERSION}."
        echo "For more information on available versions, please visit http://ftp.gnu.org/gnu/gdb."
        echo "Note: It must be executed with root permissions."
        echo
        echo "Usage: sudo ./host_gdb_install.sh [version]"
        echo " e.g.: sudo ./host_gdb_install.sh ${GDB_DEFAULT_VERSION}"
        echo
        echo "The GDB_VERSION variable can also be used instead of the parameter."
        echo " e.g.: export GDB_VERSION=\"${GDB_DEFAULT_VERSION}\""
        exit 0
    else
        GDB_VERSION=$1
    fi
fi
if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root!" 
   exit 1
fi
GDB_VERSION=${GDB_VERSION:-${GDB_DEFAULT_VERSION}}
echo "Installing ${GDB_VERSION}..."
echo

PREV_DIR=$PWD
TEMPDIR=$(mktemp -d)
cd ${TEMPDIR}
# Download GDB
wget "http://ftp.gnu.org/gnu/gdb/${GDB_VERSION}.tar.xz"
apt update
apt install --yes \
    build-essential \
    manpages-dev \
    texinfo \
    python \
    python3-venv \
    python3-dev \
    python3-pip \
    python3-venv \
    python3-wheel
# Extract the file in the current working directory
tar -xvf ${GDB_VERSION}.tar.xz

mkdir ${GDB_VERSION}/build
cd ${GDB_VERSION}/build
../configure --with-python=python3
make -j$(nproc)
make install
cd ../../ && rm -fr ${GDB_VERSION}.tar.xz ${GDB_VERSION}
cd ${PREV_DIR}
# cleanup
rm -fr ${TEMPDIR}
echo .
echo .
echo .
echo ${GDB_VERSION} successfuly installed! Start a new terminal to use it.