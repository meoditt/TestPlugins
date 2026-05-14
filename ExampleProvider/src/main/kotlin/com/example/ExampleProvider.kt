package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class WfilmizleTR : MainAPI() {
    override var mainUrl = "https://www.wfilmizle.bar"
    override var name = "Wfilmizle (Sadece TR Dublaj)"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie)

    // 1. ANA SAYFA (Sadece Türkçe Dublaj Kategorisi)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Sitenin kendi Türkçe Dublaj kategorisine istek atıyoruz
        val url = if (page == 1) "$mainUrl/turkce-dublaj-film-izle/" else "$mainUrl/turkce-dublaj-film-izle/page/$page/"
        val document = app.get(url).document
        
        val home = document.select("div.movie-preview").mapNotNull {
            it.toSearchResult()
        }
        
        return newHomePageResponse("Türkçe Dublaj Filmler", home)
    }

    // 2. ARAMA (Sadece Dublajlı Olanları Filtreler)
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        
        return document.select("div.movie-preview")
            .filter { element -> 
                // Film kartının içinde Türkçe Dublaj ikonu var mı kontrolü
                element.selectFirst("span.icon.tr") != null 
            }
            .mapNotNull {
                it.toSearchResult()
            }
    }

    // --- YARDIMCI FONKSİYON: Kartları Cloudstream Formatına Çevirme ---
    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("div.movie-poster a") ?: return null
        val href = linkElement.attr("href")
        
        val title = this.selectFirst(".movie-title")?.text()?.trim() 
            ?: linkElement.attr("title").replace(" izle", "", ignoreCase = true).trim()
        
        // Lazy Load kontrolü
        val imgElement = this.selectFirst("img")
        val posterUrl = imgElement?.attr("data-wpfc-original-src")?.takeIf { it.isNotBlank() } 
            ?: imgElement?.attr("src")

        val yearText = this.selectFirst(".movie-release-date")?.text()?.trim()
        val year = yearText?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    // 3. DETAY SAYFASI (Load)
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // Detayları çek (Yıkım Ekibi HTML kodlarına göre)
        val title = document.selectFirst(".title h1")?.text()?.replace(" izle", "", ignoreCase = true)?.trim() ?: return null
        val poster = document.selectFirst(".poster img")?.attr("src")
        val plot = document.selectFirst(".excerpt")?.text()
        val year = document.selectFirst(".release a")?.text()?.toIntOrNull()
        
        // Kategorileri " izle" ekini atarak listele
        val tags = document.select(".categories a").map { it.text().replace(" izle", "", ignoreCase = true) }
        val actors = document.select(".actor a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.tags = tags
            // Cloudstream'in ayrı bir oyuncu listesi parametresi olmadığı için, konusu ile birleştiriyoruz
            this.plot = if (actors.isNotEmpty()) "$plot\n\nOyuncular: ${actors.joinToString(", ")}" else plot
        }
    }

    // 4. VİDEO BAĞLANTILARI (LoadLinks)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data değişkeni, asıl filmin detay sayfası URL'sidir.
        val document = app.get(data).document
        
        // Iframe elementini bul ve yine lazy load attr'sine (data-wpfc-original-src) öncelik ver
        val iframe = document.selectFirst(".video-content iframe")
        val iframeUrl = iframe?.attr("data-wpfc-original-src")?.takeIf { it.isNotBlank() } 
            ?: iframe?.attr("src")

        if (iframeUrl != null && iframeUrl.startsWith("http")) {
            // "hdplayersystem.com" gibi dış bağlantıları çözmek için Cloudstream'in dahili Extractor motorunu kullanıyoruz.
            loadExtractor(iframeUrl, data, subtitleCallback, callback)
        }
        
        return true
    }
}
