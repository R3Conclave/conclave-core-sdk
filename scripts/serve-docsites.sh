#!/usr/bin/env bash

# This script runs mkdocs in local dev mode, which gives a web server that implements "hot reload".
# It isn't related to serving on the docs.conclave.net website.
#
# First argument is the port number
# Second argument if present is the directory to switch to

# Enter the Python environment where all the commands are installed to.
cd "$( dirname $0 )/../docs"

source setup-virtualenv.sh

# We must use the weird IP address to make the dev server bind to all IP addresses, not
# just localhost (which in the container is different to the host).
mkdocs serve -a 0.0.0.0:8000 &
trap "kill $(jobs -p)" SIGINT SIGTERM
