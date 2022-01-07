#!/usr/bin/env bash
set -euo pipefail

script_dir=$(dirname ${BASH_SOURCE[0]})
source $script_dir/devenv.sh

# Disable the "x" flag possibly set in other scripts.
set +x

echo
echo -e '\x1B[1m\x1B[34mWelcome to GraalVM build environment.\x1B[0m'
echo
echo "You are now in a shell inside a Docker container. "
echo -e "Run \x1B[4m./gradlew build test\x1B[0m to compile and run unit tests."
echo -e "Browse to \x1B[4mhttp://localhost:8000\x1B[0m to view the external docsite."
echo -e "Browse to \x1B[4mhttp://localhost:8001\x1B[0m to view the internal docsite."
echo


docker exec -it -e PS1="conclave \e[32m\$(git branch | awk '/^\* / { print \$2 }')\e[0m \w> " $@ $container_id bash

echo
echo "The devenv container can be shut down with one of these commands: "
echo "  - All devenv containers, use: docker stop \$(docker ps -f label=sgxjvm -q)"
echo "  - Just this one, use: docker stop $container_id"
echo
echo "Ciao!"
