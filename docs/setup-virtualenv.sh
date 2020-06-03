# This file is meant to be sourced into other scripts, not executed directly.

# We run mkdocs inside a Python "virtualenv", which is basically a sealed set of Python
# packages and a bin directory for them, fixed to the versions in requirements.txt
if [ ! -d "virtualenv" ]; then
    # Doesn't exist yet so create it and install the stuff we need.
    #
    # If the canonical working directory contains whitespace, virtualenv installs broken scripts.
    # But if we pass in an absolute path that uses symlinks to avoid whitespace, that fixes the problem.
    # If you run this script manually (not via gradle) from such a path alias, it's available in PWD:
    absolutevirtualenv="$PWD/virtualenv"
    python3 -m venv "$absolutevirtualenv"
    source virtualenv/bin/activate
    pip3 install mkdocs-material==5.2.2 
else
    source virtualenv/bin/activate
fi
