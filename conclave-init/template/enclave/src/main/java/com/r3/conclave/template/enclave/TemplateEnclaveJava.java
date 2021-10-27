package com.r3.conclave.template.enclave;

import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TemplateEnclaveJava extends Enclave {
    @Override
    protected void receiveMail(@NotNull EnclaveMail mail, @Nullable String routingHint) {
        byte[] response = postOffice(mail).encryptMail("321".getBytes());
        postMail(response, "response");
    }
}
