package com.r3.sgx.enclavelethost.server

interface ExceptionListener {
    fun notifyException(throwable: Throwable)
}
