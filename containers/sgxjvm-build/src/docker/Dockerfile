FROM ubuntu:xenial-20180417

# A build container for the full sgxjvm project.
# TODO Separate out the enclave build and make it completely reproducible.

LABEL version="1.0"
LABEL description="SGXJVM build container"
LABEL maintainer="andras.slemmer@r3.com"

RUN apt-get update -qqy
RUN apt-get install -qqy \
    curl \
    apt-transport-https \
    ca-certificates \
    software-properties-common

RUN curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
RUN add-apt-repository "deb http://apt.kubernetes.io/ kubernetes-xenial main"
RUN add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu xenial stable"
RUN apt-get update -y
RUN apt-get install -y \
    autoconf \
    ccache \
    cmake \
    cpio \
    exuberant-ctags \
    g++ \
    gcc \
    gdb \
    gdbserver \
    git \
    libcurl3 \
    libcurl4-openssl-dev \
    libprotobuf-dev \
    libssl-dev \
    libtool \
    libunwind8 \
    make \
    ocaml \
    openjdk-8-jdk \
    openssl \
    patch \
    protobuf-compiler \
    python2.7 \
    virtualenv \
    python3-venv \
    python3-dev \
    python3-pip \
    python3-venv \
    python3-wheel \
    unzip \
    wget \
    zip \
    zlib1g-dev \
    kubectl \
    docker-ce

# Some incantation that's necessary to get rid of some Python errors that appear when building docs.
# https://stackoverflow.com/a/59596814/2248578
RUN pip3 install wheel

ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV JAVA_HOME           /usr/lib/jvm/java-8-openjdk-amd64

# Without setting the locale to UTF-8 some python3 modules just refuse to work.
ENV LC_ALL              C.UTF-8
ENV LANG                C.UTF-8