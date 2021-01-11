/*
 * taken from here https://gist.github.com/bl4ck5un/31ad94ee95aa2d9460f8a375864315f2#file-cpuid_sgx-c-L21
 */

#include <stdint.h>
#include <stdio.h>

#include <iostream>
#include <sstream>

#include <string>

#define PRINTF(fmt,...) do { sprintf(sbuf, fmt, ##__VA_ARGS__); oss << sbuf; } while(0)

namespace {
unsigned POW2(unsigned n) { return 1 << n; }

void native_cpuid(unsigned int *eax, unsigned int *ebx,
                                unsigned int *ecx, unsigned int *edx)
{
        /* ecx is often an input as well as an output. */
        asm volatile("cpuid"
            : "=a" (*eax),
              "=b" (*ebx),
              "=c" (*ecx),
              "=d" (*edx)
            : "0" (*eax), "2" (*ecx));
}
}

#define title \
"\n**************************************************************************\n" \
"* CPUID Leaf %dH, Sub-Leaf %d of Intel SGX Capabilities (EAX=%dH,ECX=%d) *\n" \
"**************************************************************************\n"

namespace r3::conclave {
std::string getCpuCapabilitiesSummary()
{
  /* This program prints some CPUID information and tests the SGX support of the CPU */

  char sbuf[256];
  std::ostringstream oss;

  unsigned eax = 0,
    ebx = 0,
    ecx = 0,
    edx = 0;

  eax = 1; /* processor info and feature bits */

  native_cpuid(&eax, &ebx, &ecx, &edx);
  PRINTF("eax: %x ebx: %x ecx: %x edx: %x\n", eax, ebx, ecx, edx);

  PRINTF("stepping %d\n", eax & 0xF); // Bit 3-0
  PRINTF("model %d\n", (eax >> 4) & 0xF); // Bit 7-4
  PRINTF("family %d\n", (eax >> 8) & 0xF); // Bit 11-8
  PRINTF("processor type %d\n", (eax >> 12) & 0x3); // Bit 13-12
  PRINTF("extended model %d\n", (eax >> 16) & 0xF); // Bit 19-16
  PRINTF("extended family %d\n", (eax >> 20) & 0xFF); // Bit 27-20

  // if smx set - SGX global enable is supported
  PRINTF("smx: %d\n", (ecx >> 6) & 1); // CPUID.1:ECX.[bit6]

  /* Extended feature bits (EAX=07H, ECX=0H)*/
  PRINTF("\nExtended feature bits (EAX=07H, ECX=0H)\n");
  eax = 7;
  ecx = 0;
  native_cpuid(&eax, &ebx, &ecx, &edx);
  PRINTF("eax: %x ebx: %x ecx: %x edx: %x\n", eax, ebx, ecx, edx);

  //CPUID.(EAX=07H, ECX=0H):EBX.SGX = 1,
  PRINTF("SGX available: %d\n", (ebx >> 2) & 0x1);

  /* SGX has to be enabled in MSR.IA32_Feature_Control.SGX_Enable
    check with msr-tools: rdmsr -ax 0x3a
    SGX_Enable is Bit 18
    if SGX_Enable = 0 no leaf information will appear.
     for more information check Intel Docs Architectures-software-developer-system-programming-manual - 35.1 Architectural MSRS
  */

  /* CPUID Leaf 12H, Sub-Leaf 0 Enumeration of Intel SGX Capabilities (EAX=12H,ECX=0) */
  PRINTF(title, 12, 0, 12, 0);
  eax = 0x12;
  ecx = 0;
  native_cpuid(&eax, &ebx, &ecx, &edx);
  PRINTF("eax: %x ebx: %x ecx: %x edx: %x\n", eax, ebx, ecx, edx);

  PRINTF("SGX 1 supported: %d\n", eax & 0x1);
  PRINTF("SGX 2 supported: %d\n", (eax >> 1) & 0x1);
  PRINTF("MaxEnclaveSize not in 64-bit mode: %d MB\n", POW2((edx & 0xFF) - 20));
  PRINTF("MaxEnclaveSize in 64-bit mode: %d MB\n", POW2(((edx >> 8) & 0xFF) - 20));
  PRINTF("MISC region support: %x\n", ebx);

  /* CPUID Leaf 12H, Sub-Leaf 1 Enumeration of Intel SGX Capabilities (EAX=12H,ECX=1) */
  PRINTF(title, 12, 1, 12, 1);
  eax = 0x12;
  ecx = 1;
  native_cpuid(&eax, &ebx, &ecx, &edx);
  PRINTF("eax: %x ebx: %x ecx: %x edx: %x\n", eax, ebx, ecx, edx);
  PRINTF("DEBUG: %d\n", (eax >> 1) & 0x1);
  PRINTF("MODE64BIT: %d\n", (eax >> 2) & 0x1);
  PRINTF("Provisioning key is available: %d\n", (eax >> 4) & 0x1);
  PRINTF("EINIT token key is available: %d\n", (eax >> 5) & 0x1);

  PRINTF("XFRM[1:0]: %d\n", (ecx & 0x3));
  PRINTF("XCR0: %08x%08x\n", edx, ecx);

  for (int i = 2; i < 10; i++) {
      /* CPUID Leaf 12H, Sub-Leaf i Enumeration of Intel SGX Capabilities (EAX=12H,ECX=i) */
      eax = 0x12;
      ecx = i;
      native_cpuid(&eax, &ebx, &ecx, &edx);
      if ((eax & 0x0F) == 1) {
          PRINTF(title, 12, i, 12, i);
          PRINTF("eax: %x ebx: %x ecx: %x edx: %x\n", eax, ebx, ecx, edx);
          PRINTF("BASE address of EPC section: %08x%08x\n", ebx, (eax & 0xFFFFFFF0));
          PRINTF("SIZE of EPC section: %08x%08x\n", edx, (ecx & 0xFFFFFFF0));
          if ((ebx & 0x0F) == 1) {
              PRINTF("The EPC section is confidentiality, integrity and replay protected");
          }
      }
      else {break;}
  }

  return oss.str();
}

} // namespace r3::conclave

