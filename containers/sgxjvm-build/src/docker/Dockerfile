FROM ubuntu:20.04 AS graal
# Copy R3 version of Graal into the docker image
ARG docker_graal_tar_file=/opt/graal.tar.gz
ENV GRAAL_HOME /opt/graal
COPY /root/downloads/graal-sdk.tar.gz $docker_graal_tar_file
RUN mkdir $GRAAL_HOME && tar -C $GRAAL_HOME -xzf $docker_graal_tar_file && rm -r $docker_graal_tar_file

FROM graal

# A build container for the full sgxjvm project.
# TODO Separate out the enclave build and make it completely reproducible.

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
RUN wget -qO - https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://packages.microsoft.com/ubuntu/20.04/prod focal main"

RUN apt-get update -y && apt-get install -y \
    autoconf \
    az-dcap-client \
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
    libcurl4-openssl-dev \
    libgbm-dev \
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
    openjdk-8-jdk \
    openjdk-17-jdk \
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

ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV JAVA_HOME           /usr/lib/jvm/java-17-openjdk-amd64
ENV SGX_SDK_VERSION     2.14

# Linux SGX mitigation binaries
RUN curl -sSL -o /opt/as.ld.objdump.gold.r3.tar.gz https://download.01.org/intel-sgx/sgx-linux/$SGX_SDK_VERSION/as.ld.objdump.gold.r3.tar.gz
RUN echo "794963ca90fb0561caf3ed2bee1d9ba394226f355689cdda28161e6e0620a713  /opt/as.ld.objdump.gold.r3.tar.gz" | sha256sum -c -
RUN tar -zxf /opt/as.ld.objdump.gold.r3.tar.gz -C /usr/local/bin external/toolset/ubuntu20.04 --strip=3

# Our custom version of dokka
RUN curl -sSL -o /opt/dokka.zip https://github.com/R3Conclave/dokka/releases/download/r3-1.5.0-4/dokka.zip \
    && echo "3b6e4c1bd6c4967feb40596ce7b29f31c048ebf539cd3b157f2978325ccd55eb  /opt/dokka.zip" | sha256sum -c - \
    && mkdir -p /opt/dokka/repository/org/jetbrains/dokka/ \
    && unzip /opt/dokka.zip -d /opt/dokka/repository/org/jetbrains/dokka/ \
    && rm -v /opt/dokka.zip

# Nexus IQ
ENV NEXUS_IQ_HOME /opt/nexus-iq/
RUN mkdir -p $NEXUS_IQ_HOME
RUN wget -O $NEXUS_IQ_HOME/nexus-iq-cli.jar https://download.sonatype.com/clm/scanner/nexus-iq-cli-1.131.0-01.jar
RUN echo "f9159984056cee4576f34fcfa6f33ccab42b070e  $NEXUS_IQ_HOME/nexus-iq-cli.jar" | shasum -c -

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

# Make emacs not to use window system
RUN echo "alias emacs='emacs -nw'" >>/etc/bash.bashrc

# Make ls use colors.
RUN echo "alias ls='ls --color'" >>/etc/bash.bashrc
