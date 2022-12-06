# N.B.: Ensure the container base image exists
ARG container_base_image

FROM $container_base_image as integration-tests-build

ARG commit_id
LABEL commit_id=${commit_id}

RUN apt-get -y update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    wget \
    software-properties-common \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Intel SGX libs
RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' > /etc/apt/sources.list.d/intel-sgx.list

RUN apt-get -y update

# Disable interactive dialogue
# Otherwise, a message that requires user input might appear during the installation
# TODO: Update Conclave to Intel SGX SDK 2.18 and remove package versions: https://r3-cev.atlassian.net/browse/CON-1243
#       When removing the versions, we need to remove all the dependencies, i.e. just follow the Intel SGX SDK instructions:
#       apt-get install -y libsgx-quote-ex libsgx-dcap-ql

RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
    libsgx-ae-id-enclave=1.14.100.3-focal1 \
    libsgx-ae-le=2.17.100.3-focal1 \
    libsgx-ae-pce=2.17.100.3-focal1 \
    libsgx-ae-qe3=1.14.100.3-focal1 \
    libsgx-ae-qve=1.14.100.3-focal1 \
    libsgx-dcap-quote-verify=1.14.100.3-focal1 \
    libsgx-enclave-common=2.17.100.3-focal1 \
    libsgx-launch=2.17.100.3-focal1 \
    libsgx-pce-logic=1.14.100.3-focal1 \
    libsgx-qe3-logic=1.14.100.3-focal1 \
    libsgx-urts=2.17.100.3-focal1


# Install Docker inside integration-tests container, so it can spin another docker container to build enclaves
RUN mkdir -p /etc/apt/keyrings
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
RUN echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
RUN apt-get -y update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
