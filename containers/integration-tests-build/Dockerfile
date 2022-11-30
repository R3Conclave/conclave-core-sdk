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

# Intel DCAP libs (this should be removed once we are bundling them in the Conclave runtime along with the
# last 5 packages installed below)
RUN echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' | tee /etc/apt/sources.list.d/intel-sgx.list
RUN wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -
RUN wget -qO - https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://packages.microsoft.com/ubuntu/20.04/prod focal main"

# Disable interactive dialogue
# Otherwise, a message that requires user input might appear during the installation
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
        az-dcap-client \
        libsgx-quote-ex \
        libsgx-dcap-ql


# Install Docker inside integration-tests container, so it can spin another docker container to build enclaves
RUN mkdir -p /etc/apt/keyrings
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
RUN echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
RUN apt-get -y update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
