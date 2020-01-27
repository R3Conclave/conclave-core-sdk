#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})

source $SCRIPT_DIR/devenv.sh

echo
echo -e '\e[1m\e[34mWelcome to the Conclave development environment.\e[0m'
echo
echo "You are now in a shell inside a Docker container. "
echo -e "Run \e[4m./gradlew build test\e[0m to compile and run unit tests."
echo

docker exec -it -e PS1="conclave \e[32m\$(git branch | awk '/^\* / { print \$2 }')\e[0m \w> " $@ $CONTAINER_ID bash

echo
echo "The devenv container can be shut down with one of these commands: "
echo "  - All devenv containers, use: docker stop \$(docker ps -f label=sgxjvm -q)"
echo "  - Just this one, use: docker stop $CONTAINER_ID"
echo
echo "Ciao!"
