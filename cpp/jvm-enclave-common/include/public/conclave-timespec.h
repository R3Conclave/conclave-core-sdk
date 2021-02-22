#pragma once

#include "time.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef _STRUCT_TIMESPEC
#define _STRUCT_TIMESPEC
struct timespec {
    time_t tv_sec;
    long   tv_nsec;
};
#endif

#ifdef __cplusplus
}
#endif