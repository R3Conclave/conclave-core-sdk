FROM ubuntu:20.04

ARG commit_id

LABEL description="Intel aesmd container"
LABEL maintainer="conclave@r3.com"
LABEL commit_id=${commit_id}

# Install dependencies
RUN apt-get update -y && apt-get install -y \
    curl \
    kmod \
    patch \
    build-essential \
    libprotobuf-dev \
    libcurl4 \
    wget

RUN echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' | tee /etc/apt/sources.list.d/intel-sgx.list
RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN apt-get update -y && apt-get install -y \
    libsgx-epid \
    libsgx-quote-ex \
    libsgx-dcap-ql
# Need to set LD_LIBRARY_PATH to directory of aesm_service executable

WORKDIR /opt/intel/sgx-aesm-service/aesm
ENV LD_LIBRARY_PATH=.

# Run the service
CMD ./aesm_service --no-daemon
