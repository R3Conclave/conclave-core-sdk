FROM ubuntu:bionic-20200219

# A build container for the full sgxjvm project.
# TODO Separate out the enclave build and make it completely reproducible.

LABEL version="1.3-RC3"
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
    openjdk-11-jdk \
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

# MX and JVMCI versions should be aligned with Graal's build instructions,
# which can be found at https://github.com/oracle/graal/blob/vm-20.0.0/vm/README.md,
# where versions of other dependencies should also be listed.
ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV MX_VERSION          5.301.0
ENV JAVA_HOME           /usr/lib/jvm/java-11-openjdk-amd64
ENV JVMCI_VERSION       21.0-b03
ENV LABSJDK_VERSION     11.0.9
ENV LABSJDK_CE_11_FILE  labsjdk-ce-$LABSJDK_VERSION+10-jvmci-$JVMCI_VERSION-linux-amd64.tar.gz
ENV LABSJDK_HOME        /opt/labsjdk-ce-$LABSJDK_VERSION-jvmci-$JVMCI_VERSION
ENV JVMCI_HOME          $LABSJDK_HOME
ENV MX_HOME             /opt/mx-$MX_VERSION
ENV SGX_SDK_VERSION     2.14

# Linux SGX mitigation binaries
RUN curl -sSL -o /opt/as.ld.objdump.gold.r3.tar.gz https://download.01.org/intel-sgx/sgx-linux/$SGX_SDK_VERSION/as.ld.objdump.gold.r3.tar.gz
RUN echo "794963ca90fb0561caf3ed2bee1d9ba394226f355689cdda28161e6e0620a713  /opt/as.ld.objdump.gold.r3.tar.gz" | sha256sum -c -
RUN tar -zxf /opt/as.ld.objdump.gold.r3.tar.gz -C /usr/local/bin external/toolset/ubuntu18.04 --strip=3

# MX
# Download the right MX version
RUN git clone --depth 1 --branch $MX_VERSION  https://github.com/graalvm/mx.git /opt/mx-$MX_VERSION
# Checkout the exact commit to ensure the same code is always used even if the branch is updated
RUN git -C /opt/mx-$MX_VERSION checkout 5e0efda
RUN chmod -R a+wx /opt/mx-$MX_VERSION

# Workaround to avoid the error "fatal: unsafe repository (REPO is owned by someone else)" when
# mx executes. For more details about the error refer to: https://github.com/actions/checkout/issues/760
# However, the error message we get while using mx is not explicit about the cause as you can see in the stacktrace:
# File "/opt/mx-5.302.0/mx.py", line 5119, in execute
#   _built = self.build() \
# File "/opt/mx-5.302.0/mx_native.py", line 427, in build
#   self.subject.generate_manifest(sfc.tmpPath)
# File "/opt/mx-5.302.0/mx_native.py", line 737, in generate_manifest
#   cflags=[mx_subst.path_substitutions.substitute(cflag) for cflag in self.cflags],
# File "/opt/mx-5.302.0/mx_native.py", line 682, in cflags
#   default_cflags += [add_debug_prefix(self.suite.vc_dir)]
# File "/opt/mx-5.302.0/mx_native.py", line 680, in add_debug_prefix
#   return '-fdebug-prefix-map={}={}'.format(quote(prefix_dir), quote(mx.basename(prefix_dir)))
# File "/opt/mx-5.302.0/mx_native.py", line 678, in quote
#   return '"{}"'.format(path) if ' ' in path else path
#   TypeError: argument of type 'NoneType' is not iterable
#   Building com.oracle.jvmtiasmagent_amd64 with Ninja failed
# The work around for now is to set the directory as safe for all users in the container
RUN git config --system --add safe.directory /opt/mx-$MX_VERSION

# labs-openjdk-11
RUN curl -sSL -o /opt/$LABSJDK_CE_11_FILE https://github.com/graalvm/labs-openjdk-11/releases/download/jvmci-$JVMCI_VERSION/$LABSJDK_CE_11_FILE
RUN echo "1dd78393a9bf9084d3508e911476ae4c28932627  /opt/$LABSJDK_CE_11_FILE" | shasum -c -
RUN tar -xaf /opt/$LABSJDK_CE_11_FILE -C /opt
RUN rm -v /opt/$LABSJDK_CE_11_FILE

# Our custom version of dokka
RUN curl -sSL -o /opt/dokka.zip https://github.com/R3Conclave/dokka/releases/download/r3-1.5.0-4/dokka.zip
RUN echo "3b6e4c1bd6c4967feb40596ce7b29f31c048ebf539cd3b157f2978325ccd55eb  /opt/dokka.zip" | sha256sum -c -
RUN mkdir -p /opt/dokka/repository/org/jetbrains/dokka/
RUN unzip /opt/dokka.zip -d /opt/dokka/repository/org/jetbrains/dokka/
RUN rm -v /opt/dokka.zip

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
