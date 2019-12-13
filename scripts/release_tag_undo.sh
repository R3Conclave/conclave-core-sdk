#!/usr/bin/env bash
set -euo pipefail

[ "$#" = 1 ] || ( echo "Expecting a single argument specifying the intended release tag" && exit 1)
RELEASE=$1
[[ "$RELEASE" =~ ^[0-9]+[.][0-9]+(-rc[0-9]+)?$ ]] || (echo "Unexpected release version '$RELEASE', must be of the form 'X.Y' or X.Y-rcZ" && exit 1)
RELEASE_SPLIT=(${RELEASE//./ })

if ( git rev-list -n 1 "$RELEASE" &> /dev/null )
then
    echo "Deleting tag $RELEASE"
    git tag -d "$RELEASE"
    git push origin --delete "$RELEASE"
else
    echo "Tag isn't present, skipping deletion"
fi
