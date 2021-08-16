//
// OS Stubs for functions declared in stdio.h
//
#include "vm_enclave_layer.h"
#include "enclave_shared_data.h"

extern "C" {

static char ctime_buf[256] = {0};

char *ctime(const time_t *timep) {
    enclave_trace("ctime\n");
    return ctime_r(timep, ctime_buf);
}

char *ctime_r(const time_t*, char *buf) {
    enclave_trace("ctime_r\n");

    if (!buf) {
        errno = -EFAULT;
        return NULL;
    }
    *buf = '\0';
    return NULL;
}

void tzset() {
}

int clock_gettime(clockid_t clk_id, struct timespec *tp) {
    enclave_trace("clock_gettime\n");
    if (tp) {
        r3::conclave::EnclaveSharedData::instance().real_time(*tp);
    }
    return 0;
}

int nanosleep(const struct timespec *req, struct timespec *rem) {
     enclave_trace("nanosleep\n");
     return -1;
}

}
