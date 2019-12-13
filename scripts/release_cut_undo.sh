#!/usr/bin/env bash
set -euo pipefail

[ "$#" = 1 ] || ( echo "Expecting a single argument specifying the intended MAJOR release" && exit 1)
MAJOR=$1
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
[ "$CURRENT_BRANCH" = "master" ] || ( echo "Not on master, aborting" && exit 1 )

git diff HEAD --exit-code &> /dev/null || ( echo "Uncommitted changes, aborting" && exit 1 )

RELEASE_BRANCH_NAME="release-$MAJOR"
if ( git rev-parse --verify "$RELEASE_BRANCH_NAME" &> /dev/null )
then
    echo "Deleting local branch $RELEASE_BRANCH_NAME"
    git branch -d "$RELEASE_BRANCH_NAME"
else
    echo "Local branch $RELEASE_BRANCH_NAME doesn't exist, skipping deletion"
fi

if ( git rev-list -n 1 "origin/$RELEASE_BRANCH_NAME" &> /dev/null )
then
    echo "Deleting remote branch $RELEASE_BRANCH_NAME"
    git push --delete origin "$RELEASE_BRANCH_NAME"
else
    echo "Remote branch $RELEASE_BRANCH_NAME doesn't exist, skipping deletion"
fi

CURRENT_COMMIT=$(git rev-parse HEAD)
CURRENT_TAG=$(git describe --abbrev=0)
NIGHTLY_TAG_COMMIT=$(git rev-list -n 1 "$CURRENT_TAG")
let NEXT_MAJOR=$MAJOR+1
NEXT_NIGHTLY_TAG="$NEXT_MAJOR.0-nightly"
if [ "$CURRENT_TAG" = "$NEXT_NIGHTLY_TAG" ]
then
    git diff "$CURRENT_TAG~1" --exit-code &> /dev/null || ( echo "Source diff between HEAD and $CURRENT_TAG~1, check commits. Aborting" && exit 1 )
    echo "Resetting to $CURRENT_TAG~1"
    git reset "$CURRENT_TAG~1"
    echo "Deleting tag $CURRENT_TAG"
    git tag -d "$CURRENT_TAG"
    git push --delete origin "$CURRENT_TAG"
else
    echo "Current tag $CURRENT_TAG != $NEXT_NIGHTLY_TAG, skipping reset/delete"
fi
