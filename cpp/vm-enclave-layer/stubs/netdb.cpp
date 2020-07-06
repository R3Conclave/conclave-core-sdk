//
// OS Stubs for functions declared in netdb.h
//
#include "vm_enclave_layer.h"

extern "C" {

int getnameinfo(const struct sockaddr *addr, socklen_t addrlen,
                       char *host, socklen_t hostlen,
                       char *serv, socklen_t servlen, int flags) {
    enclave_trace("getnameinfo\n");
    return 0;
}

int getaddrinfo(const char *node, const char *service,
                const struct addrinfo *hints,
                struct addrinfo **res) {
    enclave_trace("getaddrinfo\n");
    return 0;
}

void freeaddrinfo(struct addrinfo *res) {
    enclave_trace("freeaddrinfo\n");
}

const char *gai_strerror(int errcode) {
    enclave_trace("gai_strerror\n");
    return nullptr;
}


}
