package com.anhdaden

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.plugins.*
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey

@CloudstreamPlugin
class MonPlayerPlugin : Plugin() {
    override fun load(context: Context) {
        reload()
    }

    init {
        this.openSettings = {
            try {
                val activity = it as? AppCompatActivity
                if (activity != null) {
                    val frag = MonPlayerSettingsFragment(this)
                    frag.show(activity.supportFragmentManager, "MonPlayer")
                }
            } catch (e: Exception) {
            }
        }
    }

    fun reload() {
        try {
            val savedLinks = getKey<Array<Link>>("monplayer_links") ?: emptyArray()
            savedLinks.forEach { link ->
                val pluginData = PluginManager.getPluginsOnline().find { it.internalName.contains(link.name) }
                if (pluginData != null) {
                    PluginManager.unloadPlugin(pluginData.filePath)
                } else {
                    val type = if (link.type == "Movie") {
                        setOf(TvType.Movie)
                    } else if (link.type == "Live") {
                        setOf(TvType.Live)
                    } else {
                        setOf(TvType.NSFW)
                    }
                    registerMainAPI(MonPlayerProvider(link.mainUrl, link.searchUrl, link.name, type))
                }
            }
            MainActivity.afterPluginsLoadedEvent.invoke(true)
        } catch (e: Exception) {
        }
    }
}