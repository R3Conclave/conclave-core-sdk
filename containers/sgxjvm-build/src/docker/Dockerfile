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
    software-properties-common

RUN curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
RUN add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"
RUN echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | tee -a /etc/apt/sources.list.d/kubernetes.list
RUN apt-get update -y && apt-get install -y \
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

# MX and JVMCI versions should be aligned with Graal's build instructions,
# which can be found at https://github.com/oracle/graal/blob/vm-20.0.0/vm/README.md,
# where versions of other dependencies should also be listed.
ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV MX_VERSION          5.264.4
ENV JAVA_HOME           /usr/lib/jvm/java-8-openjdk-amd64
ENV JVMCI_VERSION       20.1-b02
ENV JVMCI_FILE          openjdk-8u252+09-jvmci-$JVMCI_VERSION-linux-amd64.tar.gz
ENV JVMCI_HOME          /opt/openjdk1.8.0_252-jvmci-$JVMCI_VERSION
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
RUN echo "7c38de97840fc999001cdf9cd5f2d31734bb591aaad628f4c6b6e5b591e48bbe  /opt/$MX_VERSION.tar.gz" | sha256sum -c -
RUN tar -xaf /opt/$MX_VERSION.tar.gz -C /opt
RUN rm -v /opt/$MX_VERSION.tar.gz
RUN chmod a+wx /opt/mx-$MX_VERSION

# JVMCI-enabled JDK8
RUN curl -sSL -o /opt/$JVMCI_FILE https://github.com/graalvm/openjdk8-jvmci-builder/releases/download/jvmci-20.1-b02/$JVMCI_FILE
RUN echo "0491915b2183c183f23acc67a1d65236093c31a9ead9530655b0638049619164  /opt/$JVMCI_FILE" | sha256sum -c -
RUN tar -xaf /opt/$JVMCI_FILE -C /opt
RUN rm -v /opt/$JVMCI_FILE

# Some incantation that's necessary to get rid of some Python errors that appear when building docs.
# https://stackoverflow.com/a/59596814/2248578
RUN pip3 install wheel

# Without setting the locale to UTF-8 some python3 modules just refuse to work.
ENV LC_ALL              C.UTF-8
ENV LANG                C.UTF-8