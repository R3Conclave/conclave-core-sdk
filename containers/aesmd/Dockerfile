# This Dockerfile starts the AESM service, which is used by Gramine hardware tests.
FROM ubuntu:20.04

ARG commit_id

LABEL commit_id=${commit_id}

# Install dependencies
RUN apt-get update
RUN DEBIAN_FRONTEND=noninteractive  apt-get install -y \
    wget \
    gnupg2

RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' > /etc/apt/sources.list.d/intel-sgx.list

RUN wget -qO - https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN echo 'deb [arch=amd64] https://packages.microsoft.com/ubuntu/20.04/prod focal main' > /etc/apt/sources.list.d/msprod.list

RUN apt-get update

RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
    sgx-aesm-service \
    libsgx-ae-id-enclave \
    libsgx-ae-le \
    libsgx-ae-pce \
    libsgx-ae-qe3 \
    libsgx-ae-qve \
    libsgx-enclave-common \
    libsgx-urts \
    libsgx-qe3-logic \
    libsgx-pce-logic \
    libsgx-aesm-pce-plugin \
    libsgx-aesm-ecdsa-plugin \
    libsgx-aesm-launch-plugin \
    libsgx-aesm-quote-ex-plugin \
    libsgx-dcap-ql \
    libsgx-dcap-quote-verify \
    libsgx-launch \
    libsgx-quote-ex \
    az-dcap-client


# Need to set LD_LIBRARY_PATH to directory of aesm_service executable
WORKDIR /opt/intel/sgx-aesm-service/aesm
ENV LD_LIBRARY_PATH=.

# Run the service
CMD ./aesm_service --no-daemon
