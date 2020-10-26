FROM ubuntu:bionic-20200219

# A build container for the full sgxjvm project.
# TODO Separate out the enclave build and make it completely reproducible.

LABEL version="1.1"
LABEL description="SGXJVM build container"
LABEL maintainer="SGX@r3.com"

RUN apt-get update -y && apt-get install -y \
    curl \
    apt-transport-https \
    ca-certificates \
    software-properties-common \
    gnupg \
    wget

# CMake - instructions from https://apt.kitware.com/.
RUN wget -O - https://apt.kitware.com/keys/kitware-archive-latest.asc 2>/dev/null | gpg --dearmor - | tee /etc/apt/trusted.gpg.d/kitware.gpg >/dev/null
RUN apt-add-repository 'deb https://apt.kitware.com/ubuntu/ bionic main'
RUN apt-get install kitware-archive-keyring -y
RUN rm /etc/apt/trusted.gpg.d/kitware.gpg

# Docker and Kubernetes stuff
RUN curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
RUN add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"
RUN echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | tee -a /etc/apt/sources.list.d/kubernetes.list

# Intel DCAP libs (this should be removed once we are bundling them in the Conclave runtime)
RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu bionic main"

RUN apt-get update -y && apt-get install -y \
    autoconf \
    ccache \
    cmake \
    cpio \
    exuberant-ctags \
    g++ \
    gcc \
    gdbserver \
    git \
    libcurl4-openssl-dev \
    libprotobuf-dev \
    libssl-dev \
    libtool \
    libunwind8 \
    make \
    ocaml \
    ocamlbuild \
    openjdk-8-jdk \
    openjdk-11-jdk \
    openssl \
    patch \
    protobuf-compiler \
    python \
    python3-venv \
    python3-dev \
    python3-pip \
    python3-venv \
    python3-wheel \
    texinfo \
    unzip \
    virtualenv \
    zip \
    zlib1g-dev \
    kubectl \
    docker-ce \
    libsgx-quote-ex \
    libsgx-urts \
    libsgx-dcap-ql \
    libsgx-dcap-ql-dev

# GDB - build from source.
RUN export GDB_VERSION=gdb-9.2 \
    && export PREV_DIR=$PWD \
    && export TEMPDIR=$(mktemp -d) \
    && cd ${TEMPDIR} \
    && wget "http://ftp.gnu.org/gnu/gdb/${GDB_VERSION}.tar.xz" \
    && tar -xvf ${GDB_VERSION}.tar.xz \
    && mkdir ${GDB_VERSION}/build \
    && cd ${GDB_VERSION}/build \
    && ../configure --with-python=python3 \
    && make -j$(nproc) \
    && make install \
    && cd ../../ && rm -fr ${GDB_VERSION}.tar.xz ${GDB_VERSION} \
    && cd ${PREV_DIR} \
    && rm -fr ${TEMPDIR} \
    && unset GDB_VERSION \
    && unset PREV_DIR \
    && unset TEMPDIR

# MX and JVMCI versions should be aligned with Graal's build instructions,
# which can be found at https://github.com/oracle/graal/blob/vm-20.0.0/vm/README.md,
# where versions of other dependencies should also be listed.
ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV MX_VERSION          5.271.0
ENV JAVA_HOME           /usr/lib/jvm/java-8-openjdk-amd64
ENV JVMCI_VERSION       20.2-b03
ENV LABSJDK_VERSION     11.0.8
ENV LABSJDK_CE_11_FILE  labsjdk-ce-$LABSJDK_VERSION+10-jvmci-$JVMCI_VERSION-linux-amd64.tar.gz
ENV LABSJDK_HOME        /opt/labsjdk-ce-$LABSJDK_VERSION-jvmci-$JVMCI_VERSION
ENV JVMCI_FILE          openjdk-8u262+10-jvmci-$JVMCI_VERSION-linux-amd64.tar.gz
ENV JVMCI_HOME          /opt/openjdk1.8.0_262-jvmci-$JVMCI_VERSION
ENV MX_HOME             /opt/mx-$MX_VERSION
ENV SGX_SDK_VERSION     2.9.1

# The Avian build requires Java 8 regardless of what JAVA_HOME is set to above.
# If this variable does not exists then it defaults to using JAVA_HOME but it is
# set explicitely here so if JAVA_HOME above is changed then Avian is still built
# with the correct version
ENV JAVA_8_HOME         /usr/lib/jvm/java-8-openjdk-amd64

# Linux SGX mitigation binaries
RUN curl -sSL -o /opt/as.ld.objdump.gold.r1.tar.gz https://download.01.org/intel-sgx/sgx-linux/$SGX_SDK_VERSION/as.ld.objdump.gold.r1.tar.gz
RUN echo "eff285796ba97b4bcafc1e2c88534e486524d3295448207381cc9664a155e70d  /opt/as.ld.objdump.gold.r1.tar.gz" | sha256sum -c -
RUN tar -xaf /opt/as.ld.objdump.gold.r1.tar.gz -C /usr/local/bin --strip=2

# MX
RUN curl -sSL -o /opt/$MX_VERSION.tar.gz https://github.com/graalvm/mx/archive/$MX_VERSION.tar.gz
RUN echo "6af1cb33405dde5f74b564de36c1c62e4e9bd398096ee111adcdd4858c8aaac7 /opt/$MX_VERSION.tar.gz" | sha256sum -c -
RUN tar -xaf /opt/$MX_VERSION.tar.gz -C /opt
RUN rm -v /opt/$MX_VERSION.tar.gz
RUN chmod a+wx /opt/mx-$MX_VERSION

# JVMCI-enabled JDK8
RUN curl -sSL -o /opt/$JVMCI_FILE https://github.com/graalvm/graal-jvmci-8/releases/download/jvmci-$JVMCI_VERSION/$JVMCI_FILE
RUN echo "21eef2f252a688e900b929467187a71740128eb0  /opt/$JVMCI_FILE" | shasum -c -
RUN tar -xaf /opt/$JVMCI_FILE -C /opt
RUN rm -v /opt/$JVMCI_FILE

# labs-openjdk-11
RUN curl -sSL -o /opt/$LABSJDK_CE_11_FILE https://github.com/graalvm/labs-openjdk-11/releases/download/jvmci-$JVMCI_VERSION/$LABSJDK_CE_11_FILE
RUN echo "3ad1494babc0d8cd1f59dd659ca2dfd272c05f31  /opt/$LABSJDK_CE_11_FILE" | shasum -c -
RUN tar -xaf /opt/$LABSJDK_CE_11_FILE -C /opt
RUN rm -v /opt/$LABSJDK_CE_11_FILE

# Some incantation that's necessary to get rid of some Python errors that appear when building docs.
# https://stackoverflow.com/a/59596814/2248578
RUN pip3 install wheel

# Without setting the locale to UTF-8 some python3 modules just refuse to work.
ENV LC_ALL              C.UTF-8
ENV LANG                C.UTF-8
