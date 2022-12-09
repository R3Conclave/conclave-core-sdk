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
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
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
    libsgx-quote-ex

# Install Docker inside integration-tests container, so it can spin another docker container to build enclaves
RUN mkdir -p /etc/apt/keyrings
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
RUN echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
RUN apt-get -y update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
