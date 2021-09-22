#pragma once

#include "conclave-timespec.h"

#ifdef __cplusplus
extern "C" {
#endif

    typedef unsigned long __ino_t;
    typedef unsigned long __dev_t;
    typedef unsigned long __ino64_t;
    typedef unsigned long __nlink_t;
    typedef unsigned int __mode_t;
    typedef unsigned int __uid_t;
    typedef unsigned int __gid_t;
    typedef long __blksize_t;
    typedef long __blkcnt_t;
    typedef long __blkcnt64_t;
    typedef long __syscall_slong_t;

    
#ifndef __USE_XOPEN2K8
#define __USE_XOPEN2K8
#endif
#ifndef __x86_64__
#define __x86_64__
#endif

    // From glibc 2.27 sysdeps/unix/sysv/linux/x86/bits/stat.h


    struct stat
    {
	__dev_t st_dev;		/* Device.  */
#ifndef __x86_64__
	unsigned short int __pad1;
#endif
#if defined __x86_64__ || !defined __USE_FILE_OFFSET64
	__ino_t st_ino;		/* File serial number.	*/
#else
	__ino_t __st_ino;			/* 32bit file serial number.	*/
#endif
#ifndef __x86_64__
	__mode_t st_mode;			/* File mode.  */
	__nlink_t st_nlink;			/* Link count.  */
#else
	__nlink_t st_nlink;		/* Link count.  */
	__mode_t st_mode;		/* File mode.  */
#endif
	__uid_t st_uid;		/* User ID of the file's owner.	*/
	__gid_t st_gid;		/* Group ID of the file's group.*/
#ifdef __x86_64__
	int __pad0;
#endif
	__dev_t st_rdev;		/* Device number, if device.  */
#ifndef __x86_64__
	unsigned short int __pad2;
#endif
#if defined __x86_64__ || !defined __USE_FILE_OFFSET64
	__off_t st_size;			/* Size of file, in bytes.  */
#else
	__off64_t st_size;			/* Size of file, in bytes.  */
#endif
	__blksize_t st_blksize;	/* Optimal block size for I/O.  */
#if defined __x86_64__  || !defined __USE_FILE_OFFSET64
	__blkcnt_t st_blocks;		/* Number 512-byte blocks allocated. */
#else
	__blkcnt64_t st_blocks;		/* Number 512-byte blocks allocated. */
#endif
#ifdef __USE_XOPEN2K8
	/* Nanosecond resolution timestamps are stored in a format
	   equivalent to 'struct timespec'.  This is the type used
	   whenever possible but the Unix namespace rules do not allow the
	   identifier 'timespec' to appear in the <sys/stat.h> header.
	   Therefore we have to handle the use of this header in strictly
	   standard-compliant sources special.  */
	struct timespec st_atim;		/* Time of last access.  */
	struct timespec st_mtim;		/* Time of last modification.  */
	struct timespec st_ctim;		/* Time of last status change.  */
# define st_atime st_atim.tv_sec	/* Backward compatibility.  */
# define st_mtime st_mtim.tv_sec
# define st_ctime st_ctim.tv_sec
#else
	__time_t st_atime;			/* Time of last access.  */
	__syscall_ulong_t st_atimensec;	/* Nscecs of last access.  */
	__time_t st_mtime;			/* Time of last modification.  */
	__syscall_ulong_t st_mtimensec;	/* Nsecs of last modification.  */
	__time_t st_ctime;			/* Time of last status change.  */
	__syscall_ulong_t st_ctimensec;	/* Nsecs of last status change.  */
#endif
#ifdef __x86_64__
	__syscall_slong_t __glibc_reserved[3];
#else
# ifndef __USE_FILE_OFFSET64
	unsigned long int __glibc_reserved4;
	unsigned long int __glibc_reserved5;
# else
	__ino64_t st_ino;			/* File serial number.	*/
# endif
#endif
    };

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



#define	S_IFMT	00170000
#define S_IFSOCK 0140000
#define S_IFLNK	 0120000
#define S_IFREG  0100000
#define S_IFBLK  0060000
#define S_IFDIR  0040000
#define S_IFCHR  0020000
#define S_IFIFO  0010000
#define S_ISUID  0004000
#define S_ISGID  0002000
#define S_ISVTX  0001000

#ifdef __cplusplus
}
#endif
