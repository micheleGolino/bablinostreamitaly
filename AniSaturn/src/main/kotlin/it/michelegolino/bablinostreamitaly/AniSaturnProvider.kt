package it.michelegolino.bablinostreamitaly

import com.lagradost.api.Log
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AniSaturnProvider : MainAPI() {
    override var mainUrl = "https://www.anisaturn.net"
    override var name = "AniSaturn"
    override var lang = "it"
    override val hasMainPage = true
    override val hasQuickSearch = false

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/filter?states%5B0%5D=0&page=" to "In Corso",
        "$mainUrl/filter?states%5B0%5D=1&page=" to "Completati",
    )

    // ======================== Main Page ========================

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document

        val list = document.select(".card-anime").mapNotNull { card ->
            card.toSearchResult()
        }

        val hasNextPage = document.select("ul.pagination li.active + li a").isNotEmpty()

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = list,
                isHorizontalImages = false,
            ),
            hasNextPage
        )
    }

    // ======================== Search ========================

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/animelist?search=$query").document
        return document.select(".card-anime").mapNotNull { card ->
            card.toSearchResult()
        }
    }

    // ======================== Load (Anime Detail Page) ========================

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h2.title-accent")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: "Unknown"

        val poster = document.selectFirst("img.cover-anime")?.attr("src")
            ?: document.selectFirst(".card-body img")?.attr("src")

        val description = document.selectFirst(".trama-text")?.text()?.trim()
            ?: document.selectFirst("div#trama")?.text()?.trim()

        val genres = document.select("a[href*='/filter?categories=']").map { it.text() }

        val statusText = document.select("a[href*='/filter?states']").firstOrNull()?.text()
        val status = when (statusText?.lowercase()) {
            "in corso" -> ShowStatus.Ongoing
            "finito" -> ShowStatus.Completed
            else -> null
        }

        val isDub = title.contains("(ITA)", ignoreCase = true)

        // Parse episode list
        val episodeElements = document.select("a[href*='/episode/']")
        val episodes = episodeElements.mapNotNull { ep ->
            val epUrl = fixUrlOrNull(ep.attr("href")) ?: return@mapNotNull null
            val epText = ep.text().trim()
            val epNum = Regex("""(\d+)""").find(
                epText.substringAfter("Episodio")
            )?.groupValues?.get(1)?.toIntOrNull()

            newEpisode(epUrl) {
                this.name = epText
                this.episode = epNum
            }
        }

        val type = if (episodes.size <= 1) TvType.AnimeMovie else TvType.Anime

        return newAnimeLoadResponse(title.removeSuffix(" (ITA)"), url, type) {
            engName = title.removeSuffix(" (ITA)")
            addPoster(poster)
            addEpisodes(if (isDub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    // ======================== Load Links (Episode → Video) ========================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        Log.d("AniSaturn", "loadLinks data: $data")

        // data is the episode URL like https://www.anisaturn.net/episode/Slug-ep-1
        val episodePage = app.get(data).document

        // Find the "Guarda lo streaming" link → /watch?file=XXXX
        val watchUrl = episodePage.selectFirst("a[href*='/watch?file=']")?.attr("href")
        if (watchUrl.isNullOrEmpty()) {
            Log.e("AniSaturn", "No watch URL found on episode page")
            return false
        }

        val fullWatchUrl = fixUrl(watchUrl)
        Log.d("AniSaturn", "Watch URL: $fullWatchUrl")

        val watchPage = app.get(fullWatchUrl, referer = data).document

        // Try to find direct video source in the watch page
        // Look for video tag sources
        val videoSrc = watchPage.selectFirst("video source")?.attr("src")
            ?: watchPage.selectFirst("video")?.attr("src")

        if (!videoSrc.isNullOrEmpty()) {
            val videoUrl = if (videoSrc.startsWith("http")) videoSrc else fixUrl(videoSrc)
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = videoUrl,
                    type = if (videoUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                ) {
                    this.referer = fullWatchUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return true
        }

        // Look for iframes (external players)
        val iframes = watchPage.select("iframe")
        var found = false
        iframes.forEach { iframe ->
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotEmpty()) {
                val iframeUrl = if (iframeSrc.startsWith("http")) iframeSrc
                else if (iframeSrc.startsWith("//")) "https:$iframeSrc"
                else fixUrl(iframeSrc)

                Log.d("AniSaturn", "Found iframe: $iframeUrl")
                try {
                    loadExtractor(iframeUrl, fullWatchUrl, subtitleCallback, callback)
                    found = true
                } catch (e: Exception) {
                    Log.e("AniSaturn", "Failed to extract from iframe: $iframeUrl - ${e.message}")
                }
            }
        }

        // Look for M3U8 URLs in scripts
        val scripts = watchPage.select("script")
        scripts.forEach { script ->
            val scriptContent = script.html()

            // Look for jwplayer file source
            val jwPattern = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
            jwPattern.findAll(scriptContent).forEach { match ->
                val m3u8Url = match.groupValues[1]
                Log.d("AniSaturn", "Found JW M3U8: $m3u8Url")
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8,
                    ) {
                        this.referer = fullWatchUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                found = true
            }

            // Generic m3u8/mp4 pattern
            val genericPattern = """["'](https?://[^\s"']+\.(m3u8|mp4)[^\s"']*)["']""".toRegex()
            genericPattern.findAll(scriptContent).forEach { match ->
                val streamUrl = match.groupValues[1]
                if (!streamUrl.contains("jwplayer") && !streamUrl.contains(".js")) {
                    Log.d("AniSaturn", "Found stream URL: $streamUrl")
                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = streamUrl,
                            type = if (streamUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        ) {
                            this.referer = fullWatchUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    found = true
                }
            }
        }

        return found
    }

    // ======================== Utility ========================

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a[href*='/anime/']") ?: return null
        val url = fixUrlOrNull(anchor.attr("href")) ?: return null

        val title = this.selectFirst(".card-title, .title-anime, h5, h6")?.text()?.trim()
            ?: anchor.attr("title").trim()
            .ifEmpty { anchor.text().trim() }

        if (title.isEmpty()) return null

        val poster = this.selectFirst("img")?.let { img ->
            img.attr("src").ifEmpty { img.attr("data-src") }
        }

        val isDub = title.contains("(ITA)", ignoreCase = true)

        return newAnimeSearchResponse(title.removeSuffix(" (ITA)"), url, TvType.Anime) {
            addDubStatus(isDub)
            this.posterUrl = poster
        }
    }

    private fun fixUrlOrNull(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return fixUrl(url)
    }
}
