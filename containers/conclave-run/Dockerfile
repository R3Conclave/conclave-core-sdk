FROM ubuntu:20.04

RUN apt-get -y update

RUN DEBIAN_FRONTEND=noninteractive apt-get install -y curl
RUN curl -fsSLo /usr/share/keyrings/gramine-keyring.gpg https://packages.gramineproject.io/gramine-keyring.gpg
RUN echo 'deb [arch=amd64 signed-by=/usr/share/keyrings/gramine-keyring.gpg] https://packages.gramineproject.io/ focal main' | tee /etc/apt/sources.list.d/gramine.list
RUN DEBIAN_FRONTEND=noninteractive apt-get remove -y curl

RUN apt-get -y update

ARG gramine_version
# binutils is required to use objcopy when running jlink
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y binutils openjdk-17-jdk-headless gramine=$gramine_version

ENV JAVA_HOME /usr/lib/jvm/java-17-openjdk-amd64
