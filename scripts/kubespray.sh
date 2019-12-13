#!/usr/bin/env bash
set -xe

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
VIRTUALENV_DIR=$SCRIPT_DIR/build/virtualenv-kubespray
BUILT_FILE=$VIRTUALENV_DIR/built
if [ ! -f $BUILT_FILE ]
then
    virtualenv $VIRTUALENV_DIR
    (
        source $VIRTUALENV_DIR/bin/activate
        pip install -r $SCRIPT_DIR/requirements-kubespray.txt
        touch $BUILT_FILE
    )
fi
source $VIRTUALENV_DIR/bin/activate
$@
