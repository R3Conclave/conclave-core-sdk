FROM ubuntu:xenial-20180417

LABEL version="1.0"
LABEL description="Intel aesmd container"
LABEL maintainer="andras.slemmer@r3.com"

# Install dependencies
RUN apt-get update -qqy
RUN apt-get install -qqy \
    curl \
    kmod \
    patch \
    build-essential \
    libprotobuf9v5 \
    libcurl3

# Download and verify installer
RUN curl -sSL -o libsgx-enclave-common.deb \
    https://download.01.org/intel-sgx/sgx-linux/2.7.1/distro/ubuntu16.04-server/libsgx-enclave-common_2.7.101.3-xenial1_amd64.deb
RUN sha256sum libsgx-enclave-common.deb
RUN echo "8b423543f0b3c41c5ad3c3284e70b1e5f586eb1c72e7817e3392fe71dc3ec6ee libsgx-enclave-common.deb" | sha256sum -c -

RUN dpkg -i libsgx-enclave-common.deb

# Need to set LD_LIBRARY_PATH to directory of aesm_service executable

WORKDIR /opt/intel/libsgx-enclave-common/aesm
ENV LD_LIBRARY_PATH=.

# Run the service
CMD ./aesm_service --no-daemon
