//
// OS Stubs for functions declared in langinfo.h
//
#include "vm_enclave_layer.h"

extern "C" {

char *nl_langinfo(nl_item item) {
    if (item != CODESET) {
        enclave_trace("nl_langinfo(%d)\n", item);
    }
    return (char*) "";
}

}
