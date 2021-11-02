package com.r3.conclave.host.kds

import java.time.Duration

class KDSConfiguration constructor(val url: String) {
        var timeout: Duration = Duration.ofSeconds(10)
}
