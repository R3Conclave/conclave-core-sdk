//
// OS Stubs for functions declared in sys/epoll.h
//
#include "vm_enclave_layer.h"

extern "C" {

    int epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout) {
        enclave_trace("epoll_wait\n");
        return -1;
    }

    int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event) {
        enclave_trace("epoll_ctl\n");
        return -1;
    }

    int epoll_create(int size) {
       enclave_trace("epoll_create\n");
       return -1;
    }

}