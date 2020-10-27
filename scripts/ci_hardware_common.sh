#!/usr/bin/env bash
set -xeuo pipefail

function teardownAESM() {
    docker stop aesmd || true
    docker rm $(docker ps -a -f name=aesmd -f status=exited -q) || true
}

function buildAESMImage() {
    cd $1 && docker build --no-cache -t localhost:5000/com.r3.sgx/aesmd .
}

function startAESMContainer() {
    docker run -d --rm --name aesmd ${SGX_HARDWARE_FLAGS} localhost:5000/com.r3.sgx/aesmd
}

function stopAndRemoveAESMImage() {
    docker stop aesmd || true
    docker rmi localhost:5000/com.r3.sgx/aesmd || true
}