#!/usr/bin/env bash

source setup-virtualenv.sh

# We use the same output directory name as Sphinx for greater compatibility with the Corda scripts/infra.
mkdocs build -d build