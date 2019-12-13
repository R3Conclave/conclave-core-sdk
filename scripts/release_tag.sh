#!/usr/bin/env bash
set -euo pipefail

[ "$#" = 1 ] || ( echo "Expecting a single argument specifying the intended release tag" && exit 1)
RELEASE=$1
[[ "$RELEASE" =~ ^[0-9]+[.][0-9]+(-rc[0-9]+)?$ ]] || (echo "Unexpected release version '$RELEASE', must be of the form 'X.Y' or X.Y-rcZ" && exit 1)
RELEASE_SPLIT=(${RELEASE//./ })

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
[[ "$CURRENT_BRANCH" =~ ^release-[0-9]+$ ]] || ( echo "Not on release branch, aborting" && exit 1 )
CURRENT_BRANCH_SPLIT=(${CURRENT_BRANCH//-/ })
[[ ${RELEASE_SPLIT[0]} = ${CURRENT_BRANCH_SPLIT[1]} ]] || (echo "Specified release version $RELEASE's major doesn't match current release branch's $CURRENT_BRANCH" && exit 1)

git diff HEAD --exit-code &> /dev/null || ( echo "Uncommitted changes, aborting" && exit 1 )

if ( git rev-list -n 1 "$RELEASE" &> /dev/null )
then
    echo "Tag $RELEASE already present, skipping"
else
    echo "Tagging HEAD with $RELEASE"
    git tag -a "$RELEASE" -m "$RELEASE"
    git push --tags
fi
