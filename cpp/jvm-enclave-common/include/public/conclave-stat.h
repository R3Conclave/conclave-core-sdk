#pragma once

#include "conclave-timespec.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef __USE_XOPEN2K8
#define __USE_XOPEN2K8
#endif
#ifndef __x86_64__
#define __x86_64__
#endif

typedef unsigned long __dev_t;
typedef unsigned long __ino64_t;
typedef unsigned long __nlink_t;
typedef unsigned int __mode_t;
typedef unsigned int __uid_t;
typedef unsigned int __gid_t;
typedef long __blksize_t;
typedef long __blkcnt64_t;
typedef long __syscall_slong_t;

// From glibc 2.27 sysdeps/unix/sysv/linux/x86/bits/stat.h
struct stat64
  {
    __dev_t st_dev;             /* Device.  */
# ifdef __x86_64__
    __ino64_t st_ino;           /* File serial number.  */
    __nlink_t st_nlink;         /* Link count.  */
    __mode_t st_mode;           /* File mode.  */
# else
    unsigned int __pad1;
    __ino_t __st_ino;                   /* 32bit file serial number.    */
    __mode_t st_mode;                   /* File mode.  */
    __nlink_t st_nlink;                 /* Link count.  */
# endif
    __uid_t st_uid;             /* User ID of the file's owner. */
    __gid_t st_gid;             /* Group ID of the file's group.*/
# ifdef __x86_64__
    int __pad0;
    __dev_t st_rdev;            /* Device number, if device.  */
    __off_t st_size;            /* Size of file, in bytes.  */
# else
    __dev_t st_rdev;                    /* Device number, if device.  */
    unsigned int __pad2;
    __off64_t st_size;                  /* Size of file, in bytes.  */
# endif
    __blksize_t st_blksize;     /* Optimal block size for I/O.  */
    __blkcnt64_t st_blocks;     /* Nr. 512-byte blocks allocated.  */
# ifdef __USE_XOPEN2K8
    /* Nanosecond resolution timestamps are stored in a format
       equivalent to 'struct timespec'.  This is the type used
       whenever possible but the Unix namespace rules do not allow the
       identifier 'timespec' to appear in the <sys/stat.h> header.
       Therefore we have to handle the use of this header in strictly
       standard-compliant sources special.  */
    struct timespec st_atim;            /* Time of last access.  */
    struct timespec st_mtim;            /* Time of last modification.  */
    struct timespec st_ctim;            /* Time of last status change.  */
# else
    __time_t st_atime;                  /* Time of last access.  */
    __syscall_ulong_t st_atimensec;     /* Nscecs of last access.  */
    __time_t st_mtime;                  /* Time of last modification.  */
    __syscall_ulong_t st_mtimensec;     /* Nsecs of last modification.  */
    __time_t st_ctime;                  /* Time of last status change.  */
    __syscall_ulong_t st_ctimensec;     /* Nsecs of last status change.  */
# endif
# ifdef __x86_64__
    __syscall_slong_t __glibc_reserved[3];
# else
    __ino64_t st_ino;                   /* File serial number.          */
# endif
};
#define	S_IFMT	0170000

#ifdef __cplusplus
}
#endif