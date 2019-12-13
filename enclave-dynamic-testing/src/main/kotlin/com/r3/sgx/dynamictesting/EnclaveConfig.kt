package com.r3.sgx.dynamictesting

import java.io.OutputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

interface EnclaveConfiguration {
    val IntelSigned: Int
    val ProvisionKey: Int
    val LaunchKey: Int
    val ProdID: Int
    val ISVSVN: Int
    val TCSNum: Int
    val TCSMinPool: Int
    val TCSPolicy: Int
    val StackMaxSize: Int
    val HeapMaxSize: Int
    val HeapMinSize: Int
    val DisableDebug: Int
}

class EnclaveConfig private constructor(private val config: EnclaveConfigurationImpl) {
    @XmlRootElement(name = "EnclaveConfiguration")
    private data class EnclaveConfigurationImpl(
            @field:XmlElement override val IntelSigned: Int = 0,
            @field:XmlElement override val ProvisionKey: Int = 0,
            @field:XmlElement override val LaunchKey: Int = 0,
            @field:XmlElement override val ProdID: Int = 0,
            @field:XmlElement override val ISVSVN: Int = 0,
            @field:XmlElement override val TCSNum: Int = 8,
            @field:XmlElement override val TCSMinPool: Int = 1,
            @field:XmlElement override val TCSPolicy: Int = 1,
            @field:XmlElement override val StackMaxSize: Int = 0x280000,
            @field:XmlElement override val HeapMaxSize: Int = 0x8000000,
            @field:XmlElement override val HeapMinSize: Int = 0x8000000,
            @field:XmlElement override val DisableDebug: Int = 0
    ) : EnclaveConfiguration

    constructor() : this(EnclaveConfigurationImpl())

    override fun hashCode() = config.hashCode()
    override fun equals(other: Any?): Boolean {
        other as? EnclaveConfig ?: return false
        return config == other.config
    }

    fun getConfiguration(): EnclaveConfiguration = config

    fun withIntelSigned(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(IntelSigned = value))
    }
    fun withProvisionKey(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(ProvisionKey = value))
    }
    fun withLaunchKey(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(LaunchKey = value))
    }
    fun withProdID(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(ProdID = value))
    }
    fun withISVSVN(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(ISVSVN = value))
    }
    fun withTCSNum(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(TCSNum = value))
    }
    fun withTCSMinPool(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(TCSMinPool = value))
    }
    fun withTCSPolicy(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(TCSPolicy = value))
    }
    fun withStackMaxSize(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(StackMaxSize = value))
    }
    fun withHeapMaxSize(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(HeapMaxSize = value))
    }
    fun withHeapMinSize(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(HeapMinSize = value))
    }
    fun withDisableDebug(value: Int): EnclaveConfig {
        return EnclaveConfig(config.copy(DisableDebug = value))
    }

    fun marshal(output: OutputStream) {
        val jaxbContext = JAXBContext.newInstance(EnclaveConfigurationImpl::class.java)
        val marshaller = jaxbContext.createMarshaller()
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true)
        marshaller.marshal(config, output)
    }
}
