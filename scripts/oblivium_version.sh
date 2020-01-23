#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})

case ${OBLIVIUM_VERSIONING} in

    nightly)
        export OBLIVIUM_VERSION=${OBLIVIUM_VERSION:-$(cd "$SCRIPT_DIR/.." && git describe --long --abbrev=10)}
        [[ ${OBLIVIUM_VERSION} =~ ^[0-9]+[.][0-9]-nightly-[0-9]+-g[a-f0-9]{10}$ ]] || (echo "Unexpected nightly version '$OBLIVIUM_VERSION'" && exit 1)
        export OBLIVIUM_DEPENDENCY_VERSION=${OBLIVIUM_DEPENDENCY_VERSION:-"$(cd "$SCRIPT_DIR/.." && git describe --abbrev=0)-+"}
        [[ ${OBLIVIUM_DEPENDENCY_VERSION} =~ ^[0-9]+[.][0-9]-nightly-[+]$ ]] || (echo "Unexpected nightly dependency version '$OBLIVIUM_DEPENDENCY_VERSION'" && exit 1)
        ;;

    release)

        export OBLIVIUM_VERSION=${OBLIVIUM_VERSION:-$(cd "$SCRIPT_DIR/.." && git describe)}
        [[ ${OBLIVIUM_VERSION} =~ ^[0-9]+[.][0-9]+(-rc[0-9]+)?$ ]] || (echo "Unexpected release version '$OBLIVIUM_VERSION'" && exit 1)
        export OBLIVIUM_DEPENDENCY_VERSION=${OBLIVIUM_VERSION}
        ;;

    *)
        echo "Unexpected OBLIVIUM_VERSIONING value ${OBLIVIUM_VERSIONING}, should be 'nightly' or 'release'"
        exit 1

esac
