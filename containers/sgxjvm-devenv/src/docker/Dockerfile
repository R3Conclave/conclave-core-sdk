FROM sgxjvm-docker-nightly.software.r3.com/com.r3.sgx/sgxjvm-build:latest

# To test modifications to the build container, uncomment this line and comment out the above line.
# FROM localhost:5000/com.r3.sgx/sgxjvm-build:latest

# CLion
RUN curl -sSL -o /opt/clion.tar.gz https://download-cf.jetbrains.com/cpp/CLion-2018.1.2.tar.gz
RUN md5sum /opt/clion.tar.gz
RUN echo "7528d779cffeb3596266bf2f1c307c07 /opt/clion.tar.gz" | md5sum -c -
RUN tar -xaf /opt/clion.tar.gz -C /opt

# IDEA
RUN curl -sSL -o /opt/idea.tar.gz https://download-cf.jetbrains.com/idea/ideaIC-2018.3.tar.gz
RUN md5sum /opt/idea.tar.gz
RUN echo "eff11907e5266b548e1d75b2ec29b3dd /opt/idea.tar.gz" | md5sum -c -
RUN tar -xaf /opt/idea.tar.gz -C /opt

# Enable ptrace
COPY root/etc/sysctl.d/10-ptrace.conf /etc/sysctl.d/10-ptrace.conf
COPY root/scripts/setup-users.sh /scripts/setup-users.sh
RUN apt-get update
RUN apt-get install -qqy sudo bc && \
    echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers && \
    /scripts/setup-users.sh

# Configure a nicer/more useful prompt that doesn't show the (bogus/artificial) username, but does show git branch.
RUN echo "export PS1=\"conclave \[\e[32m\]\$(git branch | awk '/^\* / { print \$2 }')\[\e[0m\] \w> \"" >>/etc/bash.bashrc
# Make ls use colors.
RUN echo "alias ls='ls --color'" >>/etc/bash.bashrc