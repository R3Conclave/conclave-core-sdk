class EnclaveMail:
    def __init__(self, body, authenticated_sender, envelope):
        self.body = body
        self.authenticated_sender = authenticated_sender
        self.envelope = envelope

def enclave_sign(data):
    # __enclave global variable is set by PythonEnclaveAdapter
    return __enclave.pythonSign(data)

# TODO enclaveInstanceInfo API attribute

# Hacky way to convert byte arrays to python 'bytes':
# https://groups.google.com/g/jep-project/c/dIWcEQL7-UY/m/bBEdjHysAQAJ
def __convert_jbytes(jbytes):
    if jbytes is not None:
        return bytes(b % 256 for b in jbytes)
