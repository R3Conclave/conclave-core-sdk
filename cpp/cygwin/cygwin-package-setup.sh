#!/usr/bin/env bash
set -xeuo pipefail

# NOTE: cygwin doesn't have a way to download specific versions of packages, therefore the executable must be tested 
#       before distributing the dlls.
cygstart --action=runas ./setup-x86_64.exe -q --packages=gcc-core,make,wget,ocaml,ocaml-ocamlbuild,unzip,gcc-g++,binutils,openssl,libssl-devel,cygrunsr
