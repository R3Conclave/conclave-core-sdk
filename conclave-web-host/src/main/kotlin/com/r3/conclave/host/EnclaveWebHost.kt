package com.r3.conclave.host

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class EnclaveWebHost {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(EnclaveWebHost::class.java, *args)
        }
    }
}
