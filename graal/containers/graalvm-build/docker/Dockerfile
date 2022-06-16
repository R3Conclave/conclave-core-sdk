FROM ubuntu:bionic-20200219

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
  && apt-add-repository 'deb https://apt.kitware.com/ubuntu/ bionic main' \
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

# MX and JVMCI versions should be aligned with Graal's build instructions,
# which can be found at https://github.com/oracle/graal/blob/vm-20.0.0/vm/README.md,
# where versions of other dependencies should also be listed.
ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV MX_VERSION          5.301.0
ENV JVMCI_VERSION       21.0-b03
ENV LABSJDK_VERSION     11.0.9
ENV LABSJDK_MAJOR       11
ENV LABSJDK_BUILD       10
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
