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
    echo "Release branch $RELEASE_BRANCH_NAME already exists, skipping"
else
    echo "Cutting branch $RELEASE_BRANCH_NAME"
    git branch "$RELEASE_BRANCH_NAME"
fi

if ( git rev-list -n 1 "origin/$RELEASE_BRANCH_NAME" &> /dev/null )
then
    echo "Release branch $RELEASE_BRANCH_NAME already pushed to origin, skipping"
else
    echo "Pushing branch $RELEASE_BRANCH_NAME to origin"
    git push -u origin "$RELEASE_BRANCH_NAME"
fi

CURRENT_COMMIT=$(git rev-parse HEAD)
CURRENT_TAG=$(git describe --abbrev=0)
NIGHTLY_TAG_COMMIT=$(git rev-list -n 1 "$CURRENT_TAG")
let NEXT_MAJOR=$MAJOR+1
NEXT_NIGHTLY_TAG="$NEXT_MAJOR.0-nightly"
if [ "$CURRENT_TAG" = "$NEXT_NIGHTLY_TAG" ]
then
   if [ "$CURRENT_COMMIT" = "$NIGHTLY_TAG_COMMIT" ]
   then
       echo "$CURRENT_TAG tag already present, skipping"
   else
       echo "$CURRENT_TAG already present, but not on current commit, aborting"
       exit 1
   fi
else
    echo "Tagging dummy commit with $NEXT_NIGHTLY_TAG"
    git commit -m "$NEXT_NIGHTLY_TAG" --allow-empty
    git tag -a "$NEXT_NIGHTLY_TAG" -m "$NEXT_NIGHTLY_TAG"
    git push --tags
fi

echo "Success!
  Release branch: $RELEASE_BRANCH_NAME
  Next nightly tag: $NEXT_NIGHTLY_TAG"
