//
// OS Stubs for functions declared in signal.h
//
#include "vm_enclave_layer.h"

extern "C" {

int kill(pid_t, int) {
    jni_throw("Unresolved function: kill\n");
    errno = -EPERM;
    return -1;
}

int sigemptyset(sigset_t*) {
    return 0;
}

int sigfillset(sigset_t*) {
    return 0;
}

int sigaddset(sigset_t*, int) {
    return 0;
}

int sigaction(int, const struct sigaction*, struct sigaction*) {
    return 0;
}

int sigprocmask(int, const sigset_t*, sigset_t*) {
    return 0;
}

sighandler_t signal(int signum, sighandler_t handler) {
    enclave_trace("signal(%d)\n", signum);
    return handler;
}

int __libc_current_sigrtmax(void) {
    enclave_trace("__libc_current_sigrtmax\n");
    return 0;
}

int raise(int sig) {
    enclave_trace("raise\n");
    return -1;
}

}
