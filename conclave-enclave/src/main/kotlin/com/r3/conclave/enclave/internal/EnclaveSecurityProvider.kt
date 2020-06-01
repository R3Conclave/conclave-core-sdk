package com.r3.conclave.enclave.internal

import java.security.AccessController
import java.security.PrivilegedAction
import java.security.Provider
import java.security.Security

object EnclaveSecurityProvider : Provider(
        "EnclaveSecurityProvider", 0.0, "Registry of basic cryptographic algorithm for Enclave JVM"
) {
    private const val BLOCK_MODES = "ECB|CBC|PCBC|CTR|CTS|CFB|OFB" +
            "|CFB8|CFB16|CFB24|CFB32|CFB40|CFB48|CFB56|CFB64" +
            "|OFB8|OFB16|OFB24|OFB32|OFB40|OFB48|OFB56|OFB64"
    private const val BLOCK_MODES128 = BLOCK_MODES +
            "|GCM|CFB72|CFB80|CFB88|CFB96|CFB104|CFB112|CFB120|CFB128" +
            "|OFB72|OFB80|OFB88|OFB96|OFB104|OFB112|OFB120|OFB128"
    private const val BLOCK_PADS = "NOPADDING|PKCS5PADDING|ISO10126PADDING"

    private const val dsaKeyClasses = "java.security.interfaces.DSAPublicKey" + "|java.security.interfaces.DSAPrivateKey"

    private const val rsaKeyClasses = "java.security.interfaces.RSAPublicKey" + "|java.security.interfaces.RSAPrivateKey"

    /*
     * Note: RSA signature classes involved in certificate verification
     */
    private val rsaSignRegistry = mapOf(
        "KeyFactory.RSA" to "sun.security.rsa.RSAKeyFactory",
        "KeyPairGenerator.RSA" to "sun.security.rsa.RSAKeyPairGenerator",
        "Signature.MD2withRSA" to "sun.security.rsa.RSASignature\$MD2withRSA",
        "Signature.MD5withRSA" to "sun.security.rsa.RSASignature\$MD5withRSA",
        "Signature.SHA1withRSA" to "sun.security.rsa.RSASignature\$SHA1withRSA",
        "Signature.SHA224withRSA" to "sun.security.rsa.RSASignature\$SHA224withRSA",
        "Signature.SHA256withRSA" to "sun.security.rsa.RSASignature\$SHA256withRSA",
        "Signature.SHA384withRSA" to "sun.security.rsa.RSASignature\$SHA384withRSA",
        "Signature.SHA512withRSA" to "sun.security.rsa.RSASignature\$SHA512withRSA",

        "Signature.MD2withRSA SupportedKeyClasses" to rsaKeyClasses,
        "Signature.MD5withRSA SupportedKeyClasses" to rsaKeyClasses,
        "Signature.SHA1withRSA SupportedKeyClasses" to rsaKeyClasses,
        "Signature.SHA224withRSA SupportedKeyClasses" to rsaKeyClasses,
        "Signature.SHA256withRSA SupportedKeyClasses" to rsaKeyClasses,
        "Signature.SHA384withRSA SupportedKeyClasses" to rsaKeyClasses,
        "Signature.SHA512withRSA SupportedKeyClasses" to rsaKeyClasses,

        "Alg.Alias.KeyFactory.1.2.840.113549.1.1" to "RSA",
        "Alg.Alias.KeyFactory.OID.1.2.840.113549.1.1" to "RSA",

        "Alg.Alias.KeyPairGenerator.1.2.840.113549.1.1" to "RSA",
        "Alg.Alias.KeyPairGenerator.OID.1.2.840.113549.1.1" to "RSA",

        "Alg.Alias.Signature.1.2.840.113549.1.1.2" to "MD2withRSA",
        "Alg.Alias.Signature.OID.1.2.840.113549.1.1.2" to "MD2withRSA",

        "Alg.Alias.Signature.1.2.840.113549.1.1.4" to "MD5withRSA",
        "Alg.Alias.Signature.OID.1.2.840.113549.1.1.4" to "MD5withRSA",

        "Alg.Alias.Signature.1.2.840.113549.1.1.5" to "SHA1withRSA",
        "Alg.Alias.Signature.OID.1.2.840.113549.1.1.5" to "SHA1withRSA",
        "Alg.Alias.Signature.1.3.14.3.2.29" to "SHA1withRSA",

        "Alg.Alias.Signature.1.2.840.113549.1.1.14" to "SHA224withRSA",
        "Alg.Alias.Signature.OID.1.2.840.113549.1.1.14" to "SHA224withRSA",

        "Alg.Alias.Signature.1.2.840.113549.1.1.11" to "SHA256withRSA",
        "Alg.Alias.Signature.OID.1.2.840.113549.1.1.11" to "SHA256withRSA",

        "Alg.Alias.Signature.1.2.840.113549.1.1.12" to "SHA384withRSA",
        "Alg.Alias.Signature.OID.1.2.840.113549.1.1.12" to "SHA384withRSA",

        "Alg.Alias.Signature.1.2.840.113549.1.1.13" to "SHA512withRSA",
        "Alg.Alias.Signature.OID.1.2.840.113549.1.1.13" to "SHA512withRSA"
    )

    init {
        AccessController.doPrivileged(PutAllAction(this, mapOf(
                "MessageDigest.MD2" to "sun.security.provider.MD2",
                "MessageDigest.MD5" to "sun.security.provider.MD5",
                "MessageDigest.SHA" to "sun.security.provider.SHA",
                "MessageDigest.SHA-256" to "sun.security.provider.SHA2\$SHA256",
                "MessageDigest.SHA-512" to "sun.security.provider.SHA2\$SHA512",

                "Alg.Alias.MessageDigest.SHA-1" to "SHA",
                "Alg.Alias.MessageDigest.SHA1" to "SHA",
                "Alg.Alias.MessageDigest.1.3.14.3.2.26" to "SHA",
                "Alg.Alias.MessageDigest.OID.1.3.14.3.2.26" to "SHA",

                "MessageDigest.SHA-224" to "sun.security.provider.SHA2\$SHA224",
                "Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.4" to "SHA-224",
                "Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.4" to "SHA-224",

                "MessageDigest.SHA-256" to "sun.security.provider.SHA2\$SHA256",
                "Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.1" to "SHA-256",
                "Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.1" to "SHA-256",
                "MessageDigest.SHA-384" to "sun.security.provider.SHA5\$SHA384",
                "Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.2" to "SHA-384",
                "Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.2" to "SHA-384",
                "MessageDigest.SHA-512" to "sun.security.provider.SHA5\$SHA512",
                "Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.3" to "SHA-512",
                "Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.3" to "SHA-512",

                "Signature.SHA1withDSA" to "sun.security.provider.DSA\$SHA1withDSA",
                "Signature.NONEwithDSA" to "sun.security.provider.DSA\$RawDSA",
                "Alg.Alias.Signature.RawDSA" to "NONEwithDSA",
                "Signature.SHA224withDSA" to "sun.security.provider.DSA\$SHA224withDSA",
                "Signature.SHA256withDSA" to "sun.security.provider.DSA\$SHA256withDSA",

                "Signature.SHA1withDSA SupportedKeyClasses" to dsaKeyClasses,
                "Signature.NONEwithDSA SupportedKeyClasses" to dsaKeyClasses,
                "Signature.SHA224withDSA SupportedKeyClasses" to dsaKeyClasses,
                "Signature.SHA256withDSA SupportedKeyClasses" to dsaKeyClasses,

                "Alg.Alias.Signature.DSA" to "SHA1withDSA",
                "Alg.Alias.Signature.DSS" to "SHA1withDSA",
                "Alg.Alias.Signature.SHA/DSA" to "SHA1withDSA",
                "Alg.Alias.Signature.SHA-1/DSA" to "SHA1withDSA",
                "Alg.Alias.Signature.SHA1/DSA" to "SHA1withDSA",
                "Alg.Alias.Signature.SHAwithDSA" to "SHA1withDSA",
                "Alg.Alias.Signature.DSAWithSHA1" to "SHA1withDSA",
                "Alg.Alias.Signature.OID.1.2.840.10040.4.3" to "SHA1withDSA",
                "Alg.Alias.Signature.1.2.840.10040.4.3" to "SHA1withDSA",
                "Alg.Alias.Signature.1.3.14.3.2.13" to "SHA1withDSA",
                "Alg.Alias.Signature.1.3.14.3.2.27" to "SHA1withDSA",
                "Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.1" to "SHA224withDSA",
                "Alg.Alias.Signature.2.16.840.1.101.3.4.3.1" to "SHA224withDSA",
                "Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.2" to "SHA256withDSA",
                "Alg.Alias.Signature.2.16.840.1.101.3.4.3.2" to "SHA256withDSA",

                "KeyPairGenerator.DSA" to "sun.security.provider.DSAKeyPairGenerator",
                "Alg.Alias.KeyPairGenerator.OID.1.2.840.10040.4.1" to "DSA",
                "Alg.Alias.KeyPairGenerator.1.2.840.10040.4.1" to "DSA",
                "Alg.Alias.KeyPairGenerator.1.3.14.3.2.12" to "DSA",

                "Cipher.AES" to "com.sun.crypto.provider.AESCipher\$General",
                "Alg.Alias.Cipher.Rijndael" to "AES",
                "Cipher.AES SupportedModes" to BLOCK_MODES128,
                "Cipher.AES SupportedPaddings" to BLOCK_PADS,
                "Cipher.AES SupportedKeyFormats" to "RAW",

                "Cipher.AES_128/ECB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES128_ECB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.1" to "AES_128/ECB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.1" to "AES_128/ECB/NoPadding",
                "Cipher.AES_128/CBC/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES128_CBC_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.2" to "AES_128/CBC/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.2" to "AES_128/CBC/NoPadding",
                "Cipher.AES_128/OFB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES128_OFB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.3" to "AES_128/OFB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.3" to "AES_128/OFB/NoPadding",
                "Cipher.AES_128/CFB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES128_CFB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.4" to "AES_128/CFB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.4" to "AES_128/CFB/NoPadding",
                "Cipher.AES_128/GCM/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES128_GCM_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.6" to "AES_128/GCM/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.6" to "AES_128/GCM/NoPadding",

                "Cipher.AES_192/ECB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES192_ECB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.21" to "AES_192/ECB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.21" to "AES_192/ECB/NoPadding",
                "Cipher.AES_192/CBC/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES192_CBC_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.22" to "AES_192/CBC/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.22" to "AES_192/CBC/NoPadding",
                "Cipher.AES_192/OFB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES192_OFB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.23" to "AES_192/OFB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.23" to "AES_192/OFB/NoPadding",
                "Cipher.AES_192/CFB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES192_CFB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.24" to "AES_192/CFB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.24" to "AES_192/CFB/NoPadding",
                "Cipher.AES_192/GCM/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES192_GCM_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.26" to "AES_192/GCM/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.26" to "AES_192/GCM/NoPadding",

                "Cipher.AES_256/ECB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES256_ECB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.41" to "AES_256/ECB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.41" to "AES_256/ECB/NoPadding",
                "Cipher.AES_256/CBC/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES256_CBC_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.42" to "AES_256/CBC/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.42" to "AES_256/CBC/NoPadding",
                "Cipher.AES_256/OFB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES256_OFB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.43" to "AES_256/OFB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.43" to "AES_256/OFB/NoPadding",
                "Cipher.AES_256/CFB/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES256_CFB_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.44" to "AES_256/CFB/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.44" to "AES_256/CFB/NoPadding",
                "Cipher.AES_256/GCM/NoPadding" to "com.sun.crypto.provider.AESCipher\$AES256_GCM_NoPadding",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.46" to "AES_256/GCM/NoPadding",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.46" to "AES_256/GCM/NoPadding",

                "Cipher.AESWrap" to "com.sun.crypto.provider.AESWrapCipher\$General",
                "Cipher.AESWrap SupportedModes" to "ECB",
                "Cipher.AESWrap SupportedPaddings" to "NOPADDING",
                "Cipher.AESWrap SupportedKeyFormats" to "RAW",

                "Cipher.AESWrap_128" to "com.sun.crypto.provider.AESWrapCipher\$AES128",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.5" to "AESWrap_128",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.5" to "AESWrap_128",
                "Cipher.AESWrap_192" to "com.sun.crypto.provider.AESWrapCipher\$AES192",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.25" to "AESWrap_192",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.25" to "AESWrap_192",
                "Cipher.AESWrap_256" to "com.sun.crypto.provider.AESWrapCipher\$AES256",
                "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.45" to "AESWrap_256",
                "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.45" to "AESWrap_256",

                "CertPathBuilder.PKIX" to "sun.security.provider.certpath.SunCertPathBuilder",
                "CertPathBuilder.PKIX ValidationAlgorithm" to "RFC3280",

                "CertPathValidator.PKIX" to "sun.security.provider.certpath.PKIXCertPathValidator",
                "CertPathValidator.PKIX ValidationAlgorithm" to "RFC3280",

                "CertificateFactory.X.509" to "sun.security.provider.X509Factory",
                "Alg.Alias.CertificateFactory.X509" to "X.509",

                "SecureRandom.EnclaveSecureRandomSpi" to "com.r3.conclave.enclave.internal.EnclaveSecureRandomSpi"
        ) + rsaSignRegistry))
    }

    fun register() {
        Security.addProvider(this)
    }

    private class PutAllAction(private val provider: Provider, private val map: Map<*, *>) : PrivilegedAction<Void?> {
        override fun run(): Void? {
            provider.putAll(map)
            return null
        }
    }
}
