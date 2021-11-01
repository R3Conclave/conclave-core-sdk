package com.r3.conclave.host.web

import com.r3.conclave.mail.MailDecryptionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class EnclaveWebHostExceptionHandler {
    @ExceptionHandler(MailDecryptionException::class)
    fun handleMailDecryptionException(): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(ErrorType.MAIL_DECRYPTION, null), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        // TODO This is hacky! deliverMail instead should be throwing EnclaveException for exceptions coming
        //  from the enclave.
        val probablyFromEnclave = e.stackTrace.any { element ->
            element.className == "com.r3.conclave.host.EnclaveHost\$EnclaveMessageHandler"
                    && element.methodName == "sendToEnclave"
        }
        if (probablyFromEnclave) {
            return ResponseEntity(ErrorResponse(ErrorType.ENCLAVE_EXCEPTION, e.message), HttpStatus.BAD_REQUEST)
        } else {
            // Otherwise let Spring Boot register this as an internal server error.
            throw e
        }
    }

    enum class ErrorType {
        MAIL_DECRYPTION,
        ENCLAVE_EXCEPTION
    }

    class ErrorResponse(val error: ErrorType, val message: String?)
}
