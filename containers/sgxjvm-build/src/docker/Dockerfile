FROM ubuntu:bionic-20200219

# A build container for the full sgxjvm project.
# TODO Separate out the enclave build and make it completely reproducible.

LABEL version="1.1"
LABEL description="SGXJVM build container"
LABEL maintainer="SGX@r3.com"

RUN apt-get update -qqy
RUN apt-get install -qqy \
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
    libcurl4-openssl-dev \
    libprotobuf-dev \
    libssl-dev \
    libtool \
    libunwind8 \
    make \
    ocaml \
    ocamlbuild \
    openjdk-8-jdk \
    openssl \
    patch \
    protobuf-compiler \
    python \
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

# Linux SGX mitigation binaries
RUN curl -sSL -o /opt/as.ld.objdump.gold.r1.tar.gz https://download.01.org/intel-sgx/sgx-linux/2.9/as.ld.objdump.gold.r1.tar.gz
RUN echo "eff285796ba97b4bcafc1e2c88534e486524d3295448207381cc9664a155e70d  /opt/as.ld.objdump.gold.r1.tar.gz" | sha256sum -c -
RUN tar -xaf /opt/as.ld.objdump.gold.r1.tar.gz -C /usr/local/bin --strip=2

# Intel SGX SDK
RUN mkdir -p /opt/intel
RUN curl -sSL -o /opt/intel/sgx_linux_x64_sdk_2.9.100.2.bin https://download.01.org/intel-sgx/sgx-linux/2.9/distro/ubuntu18.04-server/sgx_linux_x64_sdk_2.9.100.2.bin
RUN echo "0176c9ef5d4f5294a8bb39db4f0cd4a235697d3a604fff82140a061b942a5133 /opt/intel/sgx_linux_x64_sdk_2.9.100.2.bin" | sha256sum  -c -
RUN chmod +x /opt/intel/sgx_linux_x64_sdk_2.9.100.2.bin
RUN cd /opt/intel && yes yes | ./sgx_linux_x64_sdk_2.9.100.2.bin

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