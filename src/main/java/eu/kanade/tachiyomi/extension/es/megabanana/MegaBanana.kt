package eu.kanade.tachiyomi.extension.es.megabanana

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy

class MegaBanana : HttpSource() {

    override val name = "MegaBanana"
    override val baseUrl = "https://megabanana.mx"
    override val lang = "es"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    // --- POPULAR ---
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/wp-json/megabanana/v1/catalog?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonString = response.body!!.string()
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        val items = jsonObject["items"]?.jsonArray ?: return MangasPage(emptyList(), false)

        val mangas = items.map { element ->
            val item = element.jsonObject
            SManga.create().apply {
                title = item["title"]?.jsonPrimitive?.content ?: ""
                thumbnail_url = item["thumbnail"]?.jsonPrimitive?.content ?: ""
                url = item["url"]?.jsonPrimitive?.content?.removePrefix(baseUrl) ?: ""
            }
        }

        val totalPages = jsonObject["pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val currentPage = jsonObject["page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val hasNextPage = currentPage < totalPages

        return MangasPage(mangas, hasNextPage)
    }

    // --- LATEST ---
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // --- SEARCH ---
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/wp-json/megabanana/v1/catalog?search=$query&page=$page", headers)
    }
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // --- MANGA DETAILS ---
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = Jsoup.parse(response.body!!.string())
        val title = document.select("meta[property=og:title]").attr("content").removePrefix("Leer").trim()
        val description = document.select("meta[property=og:description]").attr("content")
        
        // El sitio a veces tiene dos og:image, el primero es un icono. Buscamos el que no sea el icono.
        val thumbnail = document.select("meta[property=og:image]")
            .map { it.attr("content") }
            .firstOrNull { !it.contains("cropped-ico") } ?: ""

        return SManga.create().apply {
            this.title = title
            this.description = description
            this.thumbnail_url = thumbnail
            this.status = SManga.UNKNOWN
            this.initialized = true
        }
    }

    // --- CHAPTERS ---
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(response.body!!.string())
        val scriptContent = document.select("script:containsData(window.MegaBananaReader)").firstOrNull()?.data()
        
        if (scriptContent == null) {
            return listOf(SChapter.create().apply {
                name = "Leer en WebView (PDF Externo)"
                url = response.request.url.encodedPath
                chapter_number = 1f
            })
        }
        
        val jsonText = scriptContent.substringAfter("window.MegaBananaReader =").trim()
        val cleanJson = jsonText.substringBeforeLast("}").plus("}")
        val rootObj = json.parseToJsonElement(cleanJson).jsonObject
        val dataObj = rootObj["data"]?.jsonObject ?: return emptyList()
        
        val postId = rootObj["postId"]?.jsonPrimitive?.content ?: dataObj["postId"]?.jsonPrimitive?.content ?: return emptyList()
        val chaptersArray = dataObj["chapters"]?.jsonArray ?: return emptyList()

        return chaptersArray.mapIndexed { index, element ->
            val chap = element.jsonObject
            SChapter.create().apply {
                name = chap["title"]?.jsonPrimitive?.content ?: "Capítulo ${index + 1}"
                url = "/wp-json/megabanana/v1/reader/$postId?chapter=" + chap["number"]?.jsonPrimitive?.content
                chapter_number = (chaptersArray.size - index).toFloat()
            }
        }
    }

    // --- PAGES ---
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        // Fallback for "WebView" chapters so it doesn't crash, it just shows an error page.
        if (!response.request.url.encodedPath.contains("wp-json")) {
            return emptyList()
        }

        val jsonString = response.body!!.string()
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        val pagesArray = jsonObject["pages"]?.jsonArray ?: emptyList()

        return pagesArray.mapIndexed { i, element ->
            Page(i, "", element.jsonPrimitive.content)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("No utilizado")
}
