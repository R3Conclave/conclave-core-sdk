#!/usr/bin/env bash

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})

source ${SCRIPT_DIR}/devenv_envs.sh

if ! idea; then
    echo "Unable to start Intellij. Make sure you have idea in the PATH. An easy way to do that is to get Intellij to create it for you by going to Tools -> Create Command-line Launcher..."
    exit 1
fi
