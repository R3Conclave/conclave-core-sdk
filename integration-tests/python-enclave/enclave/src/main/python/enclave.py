# TODO Change this to use PyTorch

def receive_from_untrusted_host(bytes):
    return enclave_sign(bytes)

def receive_enclave_mail(mail):
    return mail.body[::-1]
