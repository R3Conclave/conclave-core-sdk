//
// OS Stubs for functions declared in sys/socket.h
//
#include "vm_enclave_layer.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(accept);
STUB(bind);
STUB(connect);
STUB(getsockname);
STUB(getsockopt);
STUB(listen);
STUB(recv);
STUB(recvfrom);
STUB(send);
STUB(sendto);
STUB(setsockopt);
STUB(shutdown);
STUB(socket);

extern "C" {

    int socketpair(int domain, int type, int protocol, int sv[2]) {
	return socketpair_impl(domain, type, protocol, sv);
    }

}
