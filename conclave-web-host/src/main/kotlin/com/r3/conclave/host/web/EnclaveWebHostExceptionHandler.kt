package com.r3.conclave.host.web

import com.r3.conclave.common.EnclaveException
import com.r3.conclave.mail.MailDecryptionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class EnclaveWebHostExceptionHandler {
    @ExceptionHandler(MailDecryptionException::class)
    fun handleMailDecryptionException(): ResponseEntity<ErrorResponse> {
        return generateBadRequestResponse(ErrorType.MAIL_DECRYPTION, null)
    }

    @ExceptionHandler(EnclaveException::class)
    fun handleEnclaveException(exception: EnclaveException): ResponseEntity<ErrorResponse> {
        // EnclaveException might be used as a wrapper for more specific exceptions.
        // In those cases, the EnclaveException will not have a message but the cause, which
        // is the exception being wrapped around by the EnclaveException, will
        val message = exception.message ?: exception.cause?.message
        return generateBadRequestResponse(ErrorType.ENCLAVE_EXCEPTION, message)
    }

    // For generic exceptions, let Spring Boot register this as an internal server error.

    enum class ErrorType {
        MAIL_DECRYPTION,
        ENCLAVE_EXCEPTION
    }

    class ErrorResponse(val error: ErrorType, val message: String?)

    private fun generateBadRequestResponse(errorType: ErrorType, message: String?) =
        ResponseEntity(ErrorResponse(errorType, message), HttpStatus.BAD_REQUEST)

}
