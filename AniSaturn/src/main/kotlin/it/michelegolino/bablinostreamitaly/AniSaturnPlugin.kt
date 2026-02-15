package it.michelegolino.bablinostreamitaly

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AniSaturnPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AniSaturnProvider())
    }
}
