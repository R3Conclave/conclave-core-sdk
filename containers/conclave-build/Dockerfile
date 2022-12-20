FROM ubuntu:20.04 as conclave-build

ARG commit_id
LABEL commit_id=${commit_id}

RUN apt-get -y update && apt-get install -y locales
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && locale-gen
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

# TODO curl and apt-transport-https are only needed to install gramine, so they should be run as part of a single
#  command and then removed.
# Disable interactive dialogue
# Otherwise, a message that requires user input will appear while installing Java 17
RUN apt-get -y update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
        build-essential \
        bash \
        curl \
        apt-transport-https \
        python3-pip \
        openjdk-17-jdk-headless
ENV JAVA_HOME /usr/lib/jvm/java-17-openjdk-amd64

# Add Gramine to Ubuntu repositories
RUN curl -fsSLo /usr/share/keyrings/gramine-keyring.gpg https://packages.gramineproject.io/gramine-keyring.gpg
RUN echo 'deb [arch=amd64 signed-by=/usr/share/keyrings/gramine-keyring.gpg] https://packages.gramineproject.io/ focal main' | tee /etc/apt/sources.list.d/gramine.list
RUN apt-get -y update

RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
        gramine

ARG jep_version
RUN pip3 install jep==$jep_version

