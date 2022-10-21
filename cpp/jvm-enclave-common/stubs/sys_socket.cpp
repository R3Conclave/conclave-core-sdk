//
// OS Stubs for functions declared in sys/socket.h
//
#include "vm_enclave_layer.h"

STUB(socket);

extern "C" {

    int socketpair(int domain, int type, int protocol, int sv[2]) {
	return socketpair_impl(domain, type, protocol, sv);
    }

    int accept(int sockfd, struct sockaddr *addr, socklen_t *addrlen) {
        enclave_trace("accept\n");
        errno = EINVAL;
        return - 1;
    }
    
    int bind(int sockfd, const struct sockaddr *addr, socklen_t addrlen) {
        enclave_trace("bind\n");
        errno = EINVAL;
        return -1;
    }

    int connect(int sockfd, const struct sockaddr *addr,
                socklen_t addrlen) {
        enclave_trace("connect\n");
        errno = EBADF;
        return -1;
    }

    int getsockname(int sockfd, struct sockaddr *addr, socklen_t *addrlen) {
        enclave_trace("getsockname\n");
        errno = EBADF;
        return -1;
    }

    int getsockopt(int sockfd, int level, int optname,
                   void *optval, socklen_t *optlen) {
        enclave_trace("getsockopt\n");
        errno = EBADF;
        return -1;
    }
    
    int setsockopt(int sockfd, int level, int optname,
                   const void *optval, socklen_t optlen) {
        enclave_trace("setsockopt\n");
        errno = EBADF;        
        return -1;
    }

    int listen(int sockfd, int backlog) {
        enclave_trace("listen\n");
        errno = EBADF;
        return -1;
    }

    ssize_t recv(int sockfd, void *buf, size_t len, int flags) {
        enclave_trace("recv\n");
        errno = EBADF;
        return -1;
    }

    ssize_t recvfrom(int sockfd, void *buf, size_t len, int flags,
                     struct sockaddr *src_addr, socklen_t *addrlen) {
        enclave_trace("recvfrom\n");
        errno = EBADF;
        return -1;
    }
    
    ssize_t recvmsg(int sockfd, struct msghdr *msg, int flags) {
        enclave_trace("recvmsg\n");
        errno = EBADF;
        return -1;
    }


    ssize_t send(int sockfd, const void *buf, size_t len, int flags) {
        enclave_trace("send\n");
        errno = EBADF;
        return -1;
    }

    ssize_t sendto(int sockfd, const void *buf, size_t len, int flags,
                   const struct sockaddr *dest_addr, socklen_t addrlen) {
        enclave_trace("sendto\n");
        errno = EBADF;
        return -1;
    }
    
    ssize_t sendmsg(int sockfd, const struct msghdr *msg, int flags) {
        enclave_trace("sendmsg\n");
        errno = EBADF;
        return -1;
    }

    int shutdown(int sockfd, int how) {
        enclave_trace("shutdown\n");
        errno = EBADF;
        return -1;
    }

    int socket(int domain, int type, int protocol) {
        enclave_trace("socket\n");
        errno = ENOACCES;
        return -1;        
    }
}
