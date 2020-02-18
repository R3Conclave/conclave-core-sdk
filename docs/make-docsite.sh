#!/bin/bash

# We run mkdocs inside a Python "virtualenv", which is basically a sealed set of Python
# packages fixed to the versions in requirements.txt
if [ ! -d "virtualenv" ]; then
    # Doesn't exist yet so create it and install the stuff we need.
    #
    # If the canonical working directory contains whitespace, virtualenv installs broken scripts.
    # But if we pass in an absolute path that uses symlinks to avoid whitespace, that fixes the problem.
    # If you run this script manually (not via gradle) from such a path alias, it's available in PWD:
    absolutevirtualenv="$PWD/virtualenv"
    python3 -m venv "$absolutevirtualenv"
    source virtualenv/bin/activate
    pip3 install -r requirements.txt
else
    source virtualenv/bin/activate
fi

# We use the same output directory name as Sphinx for greater compatibility with the Corda scripts/infra.
mkdocs build -d build