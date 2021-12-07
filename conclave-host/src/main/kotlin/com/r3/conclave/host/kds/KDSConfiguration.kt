package com.r3.conclave.host.kds

import java.time.Duration

// This class is intentionally not a Kotlin data class. data classes don't work well in Java and cause API nightmares,
// as we've learned from Corda. Also, default values don't work in Java
class KDSConfiguration constructor(val url: String) {
        var timeout: Duration = Duration.ofSeconds(10)
}
