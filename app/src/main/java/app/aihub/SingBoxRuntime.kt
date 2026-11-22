package app.aihub

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.util.Locale

/**
 * Small boundary around the official sing-box binding.
 *
 * Native config validation checks the generated configuration only. It is not
 * a proxy handshake or a successful HTTPS egress measurement.
 */
object SingBoxRuntime {
    const val VERSION = "1.14.4"
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val base = app.filesDir.apply { mkdirs() }
        val working = (app.getExternalFilesDir(null) ?: app.filesDir).apply { mkdirs() }
        val temp = app.cacheDir.apply { mkdirs() }
        Libbox.setLocale(Locale.getDefault().toLanguageTag())
        val options = SetupOptions().apply {
            basePath = base.absolutePath
            workingPath = working.absolutePath
            tempPath = temp.absolutePath
            logMaxLines = 1000
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
        }
        Libbox.setup(options)
        initialized = true
    }

    fun nativeVersion(): String = Libbox.version()

    fun validateConfig(configJson: String) {
        check(initialized) { "sing-box runtime 尚未初始化" }
        Libbox.checkConfig(configJson)
    }
}
