#!/usr/bin/env bash

script_dir=$(dirname ${BASH_SOURCE[0]})

if ! idea; then
    echo "Unable to start Intellij. Make sure you have idea in the PATH. An easy way to do that is to get Intellij to create it for you by going to Tools -> Create Command-line Launcher..."
    exit 1
fi
