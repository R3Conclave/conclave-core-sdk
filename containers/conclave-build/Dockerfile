FROM ubuntu:20.04 as conclave-build

ARG commit_id
LABEL commit_id=${commit_id}

RUN apt-get -y update && apt-get install -y locales
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && locale-gen
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
ENV JAVA_HOME /usr/lib/jvm/java-17-openjdk-amd64

RUN apt-get -y update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
        build-essential \
        bash

# Disable interactive dialogue
# Otherwise, a message that requires user input will appear while installing Java 17
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
        openjdk-17-jre-headless

CMD java -version
