//
// OS Stubs for functions declared in sys/utsname.h
//
#include "vm_enclave_layer.h"

extern "C" {

//
// The utsname structure contains arrays that are of a size defined by the platform
// and not by posix. We have to be careful that this structure matches the definition used
// by the substratevm builds. The definition below was taken directly from 
// <sys/utsname.h> and <bits/utsname.h> from the devenv build container to ensure they
// match.
//

// From <bits/utsname.h>
/* Length of the entries in `struct utsname' is 65.  */
#define _UTSNAME_LENGTH 65
/* Linux provides as additional information in the `struct utsname'
   the name of the current domain.  Define _UTSNAME_DOMAIN_LENGTH
   to a value != 0 to activate this entry.  */
#define _UTSNAME_DOMAIN_LENGTH _UTSNAME_LENGTH


// From <sys/utsname.h>
#ifndef _UTSNAME_SYSNAME_LENGTH
# define _UTSNAME_SYSNAME_LENGTH _UTSNAME_LENGTH
#endif
#ifndef _UTSNAME_NODENAME_LENGTH
# define _UTSNAME_NODENAME_LENGTH _UTSNAME_LENGTH
#endif
#ifndef _UTSNAME_RELEASE_LENGTH
# define _UTSNAME_RELEASE_LENGTH _UTSNAME_LENGTH
#endif
#ifndef _UTSNAME_VERSION_LENGTH
# define _UTSNAME_VERSION_LENGTH _UTSNAME_LENGTH
#endif
#ifndef _UTSNAME_MACHINE_LENGTH
# define _UTSNAME_MACHINE_LENGTH _UTSNAME_LENGTH
#endif

/* Structure describing the system and machine.  */
struct utsname {
    /* Name of the implementation of the operating system.  */
    char sysname[_UTSNAME_SYSNAME_LENGTH];

    /* Name of this node on the network.  */
    char nodename[_UTSNAME_NODENAME_LENGTH];

    /* Current release level of this implementation.  */
    char release[_UTSNAME_RELEASE_LENGTH];
    /* Current version level of this release.  */
    char version[_UTSNAME_VERSION_LENGTH];

    /* Name of the hardware type the system is running on.  */
    char machine[_UTSNAME_MACHINE_LENGTH];

#if _UTSNAME_DOMAIN_LENGTH - 0
    /* Name of the domain of this node on the network.  */
# ifdef __USE_GNU
    char domainname[_UTSNAME_DOMAIN_LENGTH];
# else
    char __domainname[_UTSNAME_DOMAIN_LENGTH];
# endif
#endif
};


int uname(struct utsname *buf) {
    enclave_trace("uname\n");

    if (!buf) {
        errno = -EFAULT;
        return -1;
    }

    memset(buf, 0, sizeof(struct utsname));
    strcpy(buf->sysname, "linuxsgx");
    strcpy(buf->nodename, "enclave");
    strcpy(buf->release, "1.0");
    strcpy(buf->version, "1.0");
    strcpy(buf->machine, "enclave");

    return 0;
}


}
