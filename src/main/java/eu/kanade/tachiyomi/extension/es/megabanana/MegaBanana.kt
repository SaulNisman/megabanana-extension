package eu.kanade.tachiyomi.extension.es.megabanana

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MegaBanana : ParsedHttpSource() {

    // --- CONFIGURACIÓN BASE ---
    override val name = "MegaBanana"
    
    // Asumimos el host principal como base url. 
    override val baseUrl = "https://megabanana.mx"
    
    override val lang = "es"
    
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // --- CATÁLOGO POPULAR / PRINCIPAL ---
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comics/page/$page/", headers)
    }

    override fun popularMangaSelector() = "div.page-item-detail, div.post-item, div.manga-item"

    override fun popularMangaNextPageSelector() = "div.nav-previous > a, a.next.page-numbers"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a, a.post-title, h4 a").first()?.let {
                title = it.text().trim()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            element.select("img").first()?.let { img ->
                thumbnail_url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            }
        }
    }

    // --- ÚLTIMAS ACTUALIZACIONES ---
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/page/$page/", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // --- BÚSQUEDA DE CÓMICS ---
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/page/$page/?s=$query&post_type=wp-manga", headers)
    }

    override fun searchMangaSelector() = "div.c-tabs-item__content, div.post-item, div.search-wrap"

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a, h4 a, a.post-title").first()?.let {
                title = it.text().trim()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            element.select("img").first()?.let { img ->
                thumbnail_url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            }
        }
    }

    // --- DETALLES DEL CÓMIC ---
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div.post-title h1, h1.entry-title").text().trim()
            author = document.select("div.author-content a, div.manga-authors a").joinToString { it.text() }
            genre = document.select("div.genres-content a, div.tags a, div.manga-genres a").joinToString { it.text() }
            description = document.select("div.summary__content, div.entry-content, div.manga-excerpt").text().trim()
            
            thumbnail_url = document.select("div.summary_image img, div.thumb img").first()?.let { img ->
                img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            }

            val statusText = document.select("div.post-status div.summary-content, div.status").text().lowercase(Locale.getDefault())
            status = when {
                statusText.contains("ongoing") || statusText.contains("en emisión") -> SManga.ONGOING
                statusText.contains("completed") || statusText.contains("finalizado") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // --- LISTA DE CAPÍTULOS ---
    override fun chapterListSelector() = "li.wp-manga-chapter, div.chapter-list li, ul.main.version-chap li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").first()?.let {
                name = it.text().trim()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            element.select("span.chapter-release-date i, span.time").first()?.text()?.let {
                date_upload = parseChapterDate(it)
            }
        }
    }

    private fun parseChapterDate(dateStr: String): Long {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy", Locale("es"))
            format.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // --- LECTOR DE PÁGINAS ---
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val imageElements = document.select("div.reading-content img, div.page-break img, div#all img")
        
        imageElements.forEachIndexed { i, element ->
            var url = element.attr("abs:data-src").trim()
            if (url.isEmpty()) url = element.attr("abs:data-lazy-src").trim()
            if (url.isEmpty()) url = element.attr("abs:data-cfsrc").trim()
            if (url.isEmpty()) url = element.attr("abs:src").trim()
            
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("No se utiliza para ParsedHttpSource")
}
