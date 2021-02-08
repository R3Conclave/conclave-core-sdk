FROM sgxjvm-docker-nightly.software.r3.com/com.r3.sgx/sgxjvm-build:latest

# To test modifications to the build container, uncomment this line and comment out the above line.
# FROM localhost:5000/com.r3.sgx/sgxjvm-build:latest

# Useful utilities
RUN apt-get update -y && apt-get install -y vim nano bc

# Configure a nicer/more useful prompt that doesn't show the (bogus/artificial) username, but does show git branch.
RUN echo "export PS1=\"conclave \[\e[32m\]\$(git branch | awk '/^\* / { print \$2 }')\[\e[0m\] \w> \"" >>/etc/bash.bashrc

# Make ls use colors.
RUN echo "alias ls='ls --color'" >>/etc/bash.bashrc