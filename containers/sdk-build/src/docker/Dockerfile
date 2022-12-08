FROM ubuntu:20.04 AS graal
# Copy R3 version of Graal into the docker image
ARG docker_graal_tar_file=/opt/graal.tar.gz
ENV GRAAL_HOME /opt/graal
COPY /root/downloads/graalvm.tar.gz $docker_graal_tar_file
RUN mkdir $GRAAL_HOME && tar -C $GRAAL_HOME -xzf $docker_graal_tar_file && rm -r $docker_graal_tar_file

FROM graal

LABEL description="Build container for the Conclave Core SDK"
LABEL maintainer="conclave@r3.com"
ARG commit_id
LABEL commit_id=${commit_id}

RUN apt-get update -y && apt-get install -y \
    curl \
    apt-transport-https \
    ca-certificates \
    software-properties-common \
    gnupg \
    wget

# CMake - instructions from https://apt.kitware.com/.
RUN wget -O - https://apt.kitware.com/keys/kitware-archive-latest.asc 2>/dev/null | gpg --dearmor - | tee /etc/apt/trusted.gpg.d/kitware.gpg >/dev/null \
  && apt-add-repository 'deb https://apt.kitware.com/ubuntu/ focal main' \
  && apt-get install kitware-archive-keyring -y \
  && rm /etc/apt/trusted.gpg.d/kitware.gpg

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
RUN add-apt-repository "deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main"

RUN apt-get update -y && apt-get install -y \
    autoconf \
    bc \
    ccache \
    cmake \
    cpio \
    debhelper \
    exuberant-ctags \
    emacs \
    g++ \
    gcc \
    gdb \
    gdbserver \
    git \
    libboost-dev \
    libboost-system-dev \
    libboost-thread-dev \
    libcurl4-openssl-dev \
    libgbm-dev \
    libprotobuf-c-dev \
    libprotobuf-dev \
    libsgx-dcap-ql \
    libsgx-dcap-ql-dev \
    libsgx-quote-ex \
    libssl-dev \
    libtool \
    libunwind8 \
    make \
    nano \
    ocaml \
    ocamlbuild \
    openjdk-17-jdk \
    openssl \
    patch \
    pkgconf \
    protobuf-compiler \
    protobuf-c-compiler \
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

ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV JAVA_HOME           /usr/lib/jvm/java-17-openjdk-amd64
ENV SGX_SDK_VERSION     2.18

# Linux SGX mitigation binaries
RUN curl -sSL -o /opt/as.ld.objdump.r4.tar.gz https://download.01.org/intel-sgx/sgx-linux/$SGX_SDK_VERSION/as.ld.objdump.r4.tar.gz
RUN echo "85dcba642ee951686cb01479be377dc5da0b4f1597014679d1a29162f0dc8160  /opt/as.ld.objdump.r4.tar.gz" | sha256sum -c -
RUN tar -zxf /opt/as.ld.objdump.r4.tar.gz -C /usr/local/bin external/toolset/ubuntu20.04 --strip=3

# Nexus IQ
ENV NEXUS_IQ_HOME /opt/nexus-iq/
RUN mkdir -p $NEXUS_IQ_HOME
RUN wget -O $NEXUS_IQ_HOME/nexus-iq-cli.jar https://download.sonatype.com/clm/scanner/nexus-iq-cli-1.131.0-01.jar
RUN echo "f9159984056cee4576f34fcfa6f33ccab42b070e  $NEXUS_IQ_HOME/nexus-iq-cli.jar" | shasum -c -

RUN pip3 install jep

# Add jep to the library path
ENV LD_LIBRARY_PATH="${LD_LIBRARY_PATH}:/usr/local/lib/python3.8/dist-packages/jep"

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

# Make emacs not to use window system
RUN echo "alias emacs='emacs -nw'" >>/etc/bash.bashrc

# Make ls use colors.
RUN echo "alias ls='ls --color'" >>/etc/bash.bashrc
