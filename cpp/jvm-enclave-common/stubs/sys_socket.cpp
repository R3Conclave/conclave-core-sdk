//
// OS Stubs for functions declared in sys/socket.h
//
#include "vm_enclave_layer.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(accept);
STUB(connect);
STUB(getsockname);
STUB(getsockopt);
STUB(listen);
STUB(recv);
STUB(send);
STUB(setsockopt);
STUB(shutdown);
STUB(socket);

extern "C" {

}
