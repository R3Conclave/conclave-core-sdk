FROM ubuntu:bionic-20200219

# A build container for the full sgxjvm project.
# TODO Separate out the enclave build and make it completely reproducible.

LABEL version="1.0"
LABEL description="SGXJVM build container"
LABEL maintainer="conclave@r3.com"

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

# Intel DCAP libs (this should be removed once we are bundling them in the Conclave runtime along with the
# last 5 packages installed below)
RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu bionic main"
RUN wget -qO - https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://packages.microsoft.com/ubuntu/18.04/prod bionic main"

RUN apt-get update -y && apt-get install -y \
    autoconf \
    az-dcap-client \
    bc \
    ccache \
    cmake \
    cpio \
    debhelper \
    exuberant-ctags \
    g++ \
    gcc \
    gdb \
    gdbserver \
    git \
    libcurl4-openssl-dev \
    libprotobuf-dev \
    libsgx-dcap-ql \
    libsgx-dcap-ql-dev \
    libsgx-quote-ex \
    libsgx-urts \
    libssl-dev \
    libtool \
    libunwind8 \
    make \
    nano \
    ocaml \
    ocamlbuild \
    openjdk-11-jdk \
    openjdk-8-jdk \
    openssl \
    patch \
    protobuf-compiler \
    python \
    python3-dev \
    python3-pip \
    python3-venv \
    python3-wheel \
    reprepro \
    sudo \
    texinfo \
    unzip \
    vim \
    virtualenv \
    zip \
    zlib1g-dev

# MX and JVMCI versions should be aligned with Graal's build instructions,
# which can be found at https://github.com/oracle/graal/blob/vm-20.0.0/vm/README.md,
# where versions of other dependencies should also be listed.
ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV MX_VERSION          5.279.4
ENV JAVA_HOME           /usr/lib/jvm/java-8-openjdk-amd64
ENV JVMCI_VERSION       21.0-b03
ENV LABSJDK_VERSION     11.0.9
ENV LABSJDK_CE_11_FILE  labsjdk-ce-$LABSJDK_VERSION+10-jvmci-$JVMCI_VERSION-linux-amd64.tar.gz
ENV LABSJDK_HOME        /opt/labsjdk-ce-$LABSJDK_VERSION-jvmci-$JVMCI_VERSION
ENV JVMCI_FILE          openjdk-8u272+10-jvmci-$JVMCI_VERSION-linux-amd64.tar.gz
ENV JVMCI_HOME          /opt/openjdk1.8.0_272-jvmci-$JVMCI_VERSION
ENV MX_HOME             /opt/mx-$MX_VERSION
ENV SGX_SDK_VERSION     2.12

# The Avian build requires Java 8 regardless of what JAVA_HOME is set to above.
# If this variable does not exists then it defaults to using JAVA_HOME but it is
# set explicitely here so if JAVA_HOME above is changed then Avian is still built
# with the correct version
ENV JAVA_8_HOME         /usr/lib/jvm/java-8-openjdk-amd64

# Linux SGX mitigation binaries
RUN curl -sSL -o /opt/as.ld.objdump.gold.r3.tar.gz https://download.01.org/intel-sgx/sgx-linux/$SGX_SDK_VERSION/as.ld.objdump.gold.r3.tar.gz
RUN echo "794963ca90fb0561caf3ed2bee1d9ba394226f355689cdda28161e6e0620a713  /opt/as.ld.objdump.gold.r3.tar.gz" | sha256sum -c -
RUN tar -zxf /opt/as.ld.objdump.gold.r3.tar.gz -C /usr/local/bin external/toolset/ubuntu18.04 --strip=3

# MX
RUN curl -sSL -o /opt/$MX_VERSION.tar.gz https://github.com/graalvm/mx/archive/$MX_VERSION.tar.gz
RUN echo "cc56f15d9a84352ee6a1379275850bb0050b7815c684e50c3692330e2de8b022 /opt/$MX_VERSION.tar.gz" | sha256sum -c -
RUN tar -xaf /opt/$MX_VERSION.tar.gz -C /opt
RUN rm -v /opt/$MX_VERSION.tar.gz
RUN chmod a+wx /opt/mx-$MX_VERSION

# JVMCI-enabled JDK8
RUN curl -sSL -o /opt/$JVMCI_FILE https://github.com/graalvm/graal-jvmci-8/releases/download/jvmci-$JVMCI_VERSION/$JVMCI_FILE
RUN echo "2ddcf577de907ad298ad361eda8895355dbbaa43  /opt/$JVMCI_FILE" | shasum -c -
RUN tar -xaf /opt/$JVMCI_FILE -C /opt
RUN rm -v /opt/$JVMCI_FILE

# labs-openjdk-11
RUN curl -sSL -o /opt/$LABSJDK_CE_11_FILE https://github.com/graalvm/labs-openjdk-11/releases/download/jvmci-$JVMCI_VERSION/$LABSJDK_CE_11_FILE
RUN echo "1dd78393a9bf9084d3508e911476ae4c28932627  /opt/$LABSJDK_CE_11_FILE" | shasum -c -
RUN tar -xaf /opt/$LABSJDK_CE_11_FILE -C /opt
RUN rm -v /opt/$LABSJDK_CE_11_FILE

# Our custom version of dokka
RUN curl -sSL -o /opt/dokka.zip https://github.com/R3Conclave/dokka/releases/download/r3-1.4.32/dokka.zip
RUN echo "92169d1ae9374a3c716f01d16da1f9e3b8767a839e82eaa06347177d688c7138  /opt/dokka.zip" | sha256sum -c -
RUN mkdir -p /opt/dokka/repository/org/jetbrains/dokka/
RUN unzip /opt/dokka.zip -d /opt/dokka/repository/org/jetbrains/dokka/
RUN rm -v /opt/dokka.zip

# Some incantation that's necessary to get rid of some Python errors that appear when building docs.
# https://stackoverflow.com/a/59596814/2248578
RUN pip3 install wheel

# Without setting the locale to UTF-8 some python3 modules just refuse to work.
ENV LC_ALL              C.UTF-8
ENV LANG                C.UTF-8

# Enable ptrace
COPY root/etc/sysctl.d/10-ptrace.conf /etc/sysctl.d/10-ptrace.conf

# Set up a fake user. Without this System.getProperty("user.home") returns '?' which confuses Corda.
COPY root/scripts/setup-users.sh /scripts/setup-users.sh
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers && /scripts/setup-users.sh

# Configure a nicer/more useful prompt that doesn't show the (bogus/artificial) username, but does show git branch.
RUN echo "export PS1=\"conclave \[\e[32m\]\$(git branch | awk '/^\* / { print \$2 }')\[\e[0m\] \w> \"" >>/etc/bash.bashrc

# Make ls use colors.
RUN echo "alias ls='ls --color'" >>/etc/bash.bashrc
