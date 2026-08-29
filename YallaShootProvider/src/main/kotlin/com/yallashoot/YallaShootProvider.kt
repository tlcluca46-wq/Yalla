package com.yallashoot

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class YallaShootProvider : MainAPI() {
    override var mainUrl = "https://yalla-shootud.com"
    override var name = "Yalla Shoot Live"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "ar"
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: ProviderCommand): HomePageResponse {
        val doc = Jsoup.connect(mainUrl)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        val homeItems = mutableListOf<SearchResponse>()

        doc.select("a.match-container, div.albopx, div.match-item, a[href*='match']").forEach { element ->
            val title = element.select(".team-name, .right-team, .left-team, .title").text().ifEmpty {
                element.attr("title").ifEmpty { element.text() }
            }
            val href = element.attr("abs:href")
            val poster = element.select("img").attr("abs:src")

            if (title.isNotEmpty() && href.isNotEmpty()) {
                homeItems.add(
                    newLiveSearchResponse(title, href, TvType.Live) {
                        this.posterUrl = poster
                    }
                )
            }
        }

        return newHomePageResponse(
            listOf(HomePageList("Partite del Giorno", homeItems.distinctBy { it.url }))
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        val title = doc.select("h1, .match-title, title").text()
        val poster = doc.select("meta[property=og:image]").attr("content")

        return newLiveStreamLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = Jsoup.connect(data)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        var foundLink = false

        doc.select("iframe").forEach { iframe ->
            val iframeSrc = iframe.attr("abs:src")
            if (iframeSrc.isNotEmpty()) {
                val loaded = loadExtractor(iframeSrc, data, subtitleCallback, callback)
                if (loaded) foundLink = true
            }
        }

        val htmlContent = doc.html()
        val m3u8Regex = """https?://[^\s"'<>]+?\.m3u8""".toRegex()
        m3u8Regex.findAll(htmlContent).forEach { match ->
            val videoUrl = match.value
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Stream Direct HLS",
                    url = videoUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
            foundLink = true
        }

        return foundLink
    }
}
