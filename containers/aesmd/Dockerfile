FROM ubuntu:20.04

ARG commit_id

LABEL commit_id=${commit_id}

# Install dependencies
RUN apt-get update
RUN DEBIAN_FRONTEND=noninteractive  apt-get install -y \
    wget

RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' > /etc/apt/sources.list.d/intel-sgx.list

RUN wget -qO - https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN echo 'deb [arch=amd64] https://packages.microsoft.com/ubuntu/20.04/prod focal main' > /etc/apt/sources.list.d/msprod.list

RUN apt-get update
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
    sgx-aesm-service=2.17.100.3-focal1 \
    libsgx-ae-epid=2.17.100.3-focal1 \
    libsgx-ae-id-enclave=1.14.100.3-focal1 \
    libsgx-ae-le=2.17.100.3-focal1 \
    libsgx-ae-pce=2.17.100.3-focal1 \
    libsgx-ae-qe3=1.14.100.3-focal1 \
    libsgx-ae-qve=1.14.100.3-focal1 \
    libsgx-aesm-ecdsa-plugin=2.17.100.3-focal1 \
    libsgx-aesm-epid-plugin=2.17.100.3-focal1 \
    libsgx-aesm-launch-plugin=2.17.100.3-focal1 \
    libsgx-aesm-pce-plugin=2.17.100.3-focal1 \
    libsgx-aesm-quote-ex-plugin=2.17.100.3-focal1 \
    libsgx-dcap-quote-verify=1.14.100.3-focal1 \
    libsgx-enclave-common=2.17.100.3-focal1 \
    libsgx-launch=2.17.100.3-focal1 \
    libsgx-pce-logic=1.14.100.3-focal1 \
    libsgx-qe3-logic=1.14.100.3-focal1 \
    libsgx-urts=2.17.100.3-focal1 \
    az-dcap-client

# Need to set LD_LIBRARY_PATH to directory of aesm_service executable
WORKDIR /opt/intel/sgx-aesm-service/aesm
ENV LD_LIBRARY_PATH=.

# Run the service
CMD ./aesm_service --no-daemon
