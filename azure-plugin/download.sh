wget https://packages.microsoft.com/ubuntu/18.04/prod/pool/main/a/az-dcap-client/az-dcap-client_1.6_amd64.deb && ar x az-dcap-client_1.6_amd64.deb data.tar.xz && tar xvJf data.tar.xz --transform='s/.*\///' ./usr/lib/libdcap_quoteprov.so && rm az-dcap-client_1.6_amd64.deb data.tar.xz

