package it.michelegolino.bablinostreamitaly

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class HattrickExtractor : ExtractorApi() {
    override val mainUrl = "https://htsport.ws"
    override val name = "Hattrick"
    override val requiresReferer = true
    
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (!url.contains("htsport.ws")) return
        
        try {
            val headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to mainUrl,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
            )
            
            Log.d("HattrickExtractor", "Getting page: $url")
            val response = app.get(url, headers = headers)
            val document = response.document
            
            // Cerca iframe nella pagina
            val iframes = document.select("iframe")
            Log.d("HattrickExtractor", "Found ${iframes.size} iframes")
            
            iframes.forEach { iframe ->
                val iframeSrc = iframe.attr("src")
                Log.d("HattrickExtractor", "Iframe src: $iframeSrc")
                
                if (iframeSrc.isNotEmpty()) {
                    val fullUrl = when {
                        iframeSrc.startsWith("http") -> iframeSrc
                        iframeSrc.startsWith("//") -> "https:$iframeSrc"
                        else -> "$mainUrl/$iframeSrc"
                    }
                    
                    // Tenta di estrarre lo stream dall'iframe
                    extractFromIframe(fullUrl, url, callback)
                }
            }
            
            // Cerca anche tag video direttamente nella pagina
            val videos = document.select("video source, video")
            videos.forEach { video ->
                val src = video.attr("src")
                if (src.isNotEmpty() && (src.contains("m3u8") || src.contains("mp4"))) {
                    Log.d("HattrickExtractor", "Found direct video: $src")
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            src,
                            mainUrl,
                            Qualities.Unknown.value,
                            type = if (src.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        ) {
                            this.referer = url
                        }
                    )
                }
            }
            
            // Cerca URL M3U8 nel codice JavaScript
            val scripts = document.select("script")
            scripts.forEach { script ->
                val scriptContent = script.html()
                val m3u8Pattern = """(https?://[^\s"']+\.m3u8[^\s"']*)""".toRegex()
                val matches = m3u8Pattern.findAll(scriptContent)
                
                matches.forEach { match ->
                    val m3u8Url = match.groupValues[1]
                    Log.d("HattrickExtractor", "Found M3U8 in script: $m3u8Url")
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            m3u8Url,
                            mainUrl,
                            Qualities.Unknown.value,
                            type = ExtractorLinkType.M3U8,
                        ) {
                            this.referer = url
                        }
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e("HattrickExtractor", "Error extracting: ${e.message}")
        }
    }
    
    private suspend fun extractFromIframe(
        iframeUrl: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to referer,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
            )
            
            Log.d("HattrickExtractor", "Getting iframe: $iframeUrl")
            val response = app.get(iframeUrl, headers = headers)
            val document = response.document
            
            // Cerca video source nell'iframe
            val sources = document.select("video source, source")
            sources.forEach { source ->
                val src = source.attr("src")
                if (src.isNotEmpty()) {
                    val fullSrc = when {
                        src.startsWith("http") -> src
                        src.startsWith("//") -> "https:$src"
                        else -> {
                            val base = iframeUrl.substringBeforeLast("/")
                            "$base/$src"
                        }
                    }
                    
                    Log.d("HattrickExtractor", "Found source in iframe: $fullSrc")
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            fullSrc,
                            iframeUrl,
                            Qualities.Unknown.value,
                            type = if (fullSrc.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        ) {
                            this.referer = referer
                        }
                    )
                }
            }
            
            // Cerca M3U8 negli script dell'iframe
            val scripts = document.select("script")
            scripts.forEach { script ->
                val scriptContent = script.html()
                
                // Pattern per trovare URL M3U8
                val patterns = listOf(
                    """["']?(https?://[^\s"']+\.m3u8[^\s"']*)["']?""".toRegex(),
                    """"file":\s*["']([^"']+)["']""".toRegex(),
                    """source:\s*["']([^"']+)["']""".toRegex(),
                    """src:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
                )
                
                patterns.forEach { pattern ->
                    val matches = pattern.findAll(scriptContent)
                    matches.forEach { match ->
                        if (match.groupValues.size > 1) {
                            val streamUrl = match.groupValues[1]
                            if (streamUrl.contains("m3u8") || streamUrl.contains("mp4")) {
                                Log.d("HattrickExtractor", "Found stream URL: $streamUrl")
                                callback(
                                    newExtractorLink(
                                        name,
                                        name,
                                        streamUrl,
                                        iframeUrl,
                                        Qualities.Unknown.value,
                                        type = if (streamUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                    ) {
                                        this.referer = referer
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("HattrickExtractor", "Error extracting from iframe: ${e.message}")
        }
    }
}
