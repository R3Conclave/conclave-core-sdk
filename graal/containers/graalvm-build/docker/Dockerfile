FROM ubuntu:20.04

# A build container for Graal project.

LABEL description="R3 GraalVM build container"
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

RUN apt-get update -y && apt-get install -y \
    cmake \
    g++ \
    git \
    libtool \
    libunwind8 \
    patch \
    python \
    python3 \
    python3-dev \
    vim \
    zlib1g-dev

# MX and JVMCI versions should be aligned with Graal's build.
# For MX you can find the version in the Graal repository, under the tools/mx.tools/suite.py file,
#   for example https://github.com/oracle/graal/blob/release/graal-vm/21.3/tools/mx.tools/suite.py
# For JVMCI you can search in the common.json file, for example
#   https://github.com/oracle/graal/blob/release/graal-vm/21.3/common.json
# We are currently using labsjdk-ce-17 version.
# Also, note that in order to apply such changes when building Graal locally,
#   you need to avoid to download Graal from the Docker repository by setting
#   "export DOCKER_IMAGE_PULL=0" in your ~/.oblivium_credentials.sh and
#   rebuild your container from scratch.
# Finally, you also need to find the revision related to the mx version (see below).
#    You can clone the mx repository here git@github.com:graalvm/mx.git
#    and then find the commit revision with a "git log",
#    for example, "git log tags/5.310.0" 
ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV MX_VERSION          5.310.0
ENV JVMCI_VERSION       21.3-b09
ENV LABSJDK_VERSION     17.0.2
ENV LABSJDK_MAJOR       17
ENV LABSJDK_BUILD       8
ENV LABSJDK_CE_FILE     labsjdk-ce-$LABSJDK_VERSION+$LABSJDK_BUILD-jvmci-$JVMCI_VERSION-linux-amd64.tar.gz
ENV LABSJDK_HOME        /opt/labsjdk-ce-$LABSJDK_VERSION-jvmci-$JVMCI_VERSION
ENV JVMCI_HOME          $LABSJDK_HOME
ENV JAVA_HOME           $JVMCI_HOME
ENV MX_HOME             /opt/mx-$MX_VERSION

# MX version 5.294.1 must use Python 3 otherwise the
# exception 'ImportError: No module named pathlib' is thrown
ENV MX_PYTHON_VERSION   3

# Environment variable to let scripts detect if the docker image is being used
ENV DOCKER_CONTAINER_IS_RUNNING TRUE

# MX
# Download the right MX version
RUN git clone --depth 1 --branch $MX_VERSION  https://github.com/graalvm/mx.git /opt/mx-$MX_VERSION
# Checkout the exact commit to ensure the same code is always used even if the branch is updated
RUN git -C /opt/mx-$MX_VERSION checkout 522b851
RUN chmod -R a+wx /opt/mx-$MX_VERSION

# labs-openjdk-17
RUN curl -sSL -o /opt/$LABSJDK_CE_FILE https://github.com/graalvm/labs-openjdk-$LABSJDK_MAJOR/releases/download/jvmci-$JVMCI_VERSION/$LABSJDK_CE_FILE \
    && tar -xaf /opt/$LABSJDK_CE_FILE -C /opt \
    && rm -v /opt/$LABSJDK_CE_FILE


# Set up a fake user. Without this System.getProperty("user.home") returns '?' which confuses Corda.
COPY root/scripts/setup-users.sh /scripts/setup-users.sh
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers && /scripts/setup-users.sh

# Configure a nicer/more useful prompt that doesn't show the (bogus/artificial) username, but does show git branch.
RUN echo "export PS1=\"conclave \[\e[32m\]\[\e[0m\] \w> \"" >>/etc/bash.bashrc

# Make emacs not to use window system
RUN echo "alias emacs='emacs -nw'" >>/etc/bash.bashrc

# Make ls use colors.
RUN echo "alias ls='ls --color'" >>/etc/bash.bashrc
