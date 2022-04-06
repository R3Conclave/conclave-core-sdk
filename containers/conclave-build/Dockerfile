FROM ubuntu:20.04

RUN apt-get -y update && apt-get install -y locales
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && locale-gen
ENV LANG en_US.UTF-8  
ENV LANGUAGE en_US:en  
ENV LC_ALL en_US.UTF-8

RUN apt-get -y update && apt-get install -y build-essential bash
# Disable interactive dialogue
# Otherwise, a message that requires user input will appear while installing Java 17
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get install -y openjdk-17-jre-headless

CMD java -version
