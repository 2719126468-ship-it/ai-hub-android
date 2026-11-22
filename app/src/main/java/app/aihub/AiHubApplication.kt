package app.aihub

import android.app.Application
import android.util.Log

class AiHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { SingBoxRuntime.initialize(this) }
            .onFailure { Log.e("AIHub", "sing-box initialization failed", it) }
    }
}
