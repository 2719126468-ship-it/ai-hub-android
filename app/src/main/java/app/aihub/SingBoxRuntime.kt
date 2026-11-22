package app.aihub

import io.nekohasekai.libbox.Libbox

/**
 * Small, explicit boundary around the official sing-box binding.
 *
 * Config validation is native sing-box validation, not a TCP reachability check.
 * A node is not considered usable until the runtime can start it and an HTTP
 * request succeeds through its local proxy inbound.
 */
object SingBoxRuntime {
    const val VERSION = "1.14.4"

    fun nativeVersion(): String = Libbox.version()

    fun validateConfig(configJson: String) {
        Libbox.checkConfig(configJson)
    }
}
