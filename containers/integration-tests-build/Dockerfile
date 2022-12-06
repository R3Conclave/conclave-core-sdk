# N.B.: Ensure the container base image exists
ARG container_base_image

FROM $container_base_image as integration-tests-build

ARG commit_id
LABEL commit_id=${commit_id}

RUN apt-get -y update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    wget \
    software-properties-common

# Intel SGX libs
RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' > /etc/apt/sources.list.d/intel-sgx.list

RUN apt-get -y update

# Disable interactive dialogue
# Otherwise, a message that requires user input might appear during the installation
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
    libsgx-quote-ex \
    libsgx-dcap-ql
