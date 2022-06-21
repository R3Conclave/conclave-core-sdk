FROM ubuntu:20.04

ARG commit_id
LABEL commit_id=${commit_id}

RUN apt-get -y update && apt-get install -y locales
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && locale-gen
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

RUN apt-get -y update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
        build-essential \
        bash \
        wget \
        software-properties-common

# Intel DCAP libs (this should be removed once we are bundling them in the Conclave runtime along with the
# last 5 packages installed below)
RUN wget -qO - https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://packages.microsoft.com/ubuntu/20.04/prod focal main"

# Set up a fake user. Without this System.getProperty("user.home") returns '?' which confuses Corda.
COPY root/scripts/setup-users.sh /scripts/setup-users.sh
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers && /scripts/setup-users.sh

# Disable interactive dialogue
# Otherwise, a message that requires user input will appear while installing Java 17
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jre-headless \
        az-dcap-client \
        openjdk-8-jdk-headless # The cordapp requires Java 8 to run

CMD java -version
