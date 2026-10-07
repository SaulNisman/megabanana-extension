package eu.kanade.tachiyomi.extension.es.megabanana

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp.Request
import okhttp.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class MegaBanana : HttpSource() {

    override val name = "MegaBanana"
    override val baseUrl = "https://megabanana.mx"
    override val lang = "es"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    // --- POPULAR MANGA ---
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/wp-json/megabanana/v1/catalog?pagina=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonString = response.body.string()
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        val items = jsonObject["items"]?.jsonArray ?: return MangasPage(emptyList(), false)
        val page = jsonObject["page"]?.jsonPrimitive?.int ?: 1
        val totalPages = jsonObject["pages"]?.jsonPrimitive?.int ?: 1

        val mangas = items.map { element ->
            val item = element.jsonObject
            SManga.create().apply {
                title = item["title"]?.jsonPrimitive?.content ?: ""
                thumbnail_url = item["thumbnail"]?.jsonPrimitive?.content ?: ""
                url = item["url"]?.jsonPrimitive?.content?.removePrefix(baseUrl) ?: ""
            }
        }
        return MangasPage(mangas, page < totalPages)
    }

    // --- LATEST UPDATES ---
    override fun latestUpdatesRequest(page: Int): Request {
        return popularMangaRequest(page)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // --- SEARCH MANGA ---
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/wp-json/megabanana/v1/catalog?buscar=$query&pagina=$page", headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // --- MANGA DETAILS ---
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    private fun parseReaderData(response: Response): kotlinx.serialization.json.JsonObject {
        val document = Jsoup.parse(response.body.string())
        val scriptContent = document.select("script#megabanana-reader-data").firstOrNull()?.data()
            ?: throw Exception("No se encontraron los datos del lector en la página")
        return json.parseToJsonElement(scriptContent).jsonObject
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = parseReaderData(response)
        val comic = data["comic"]?.jsonObject ?: throw Exception("Datos del comic no encontrados")

        return SManga.create().apply {
            title = comic["title"]?.jsonPrimitive?.content ?: ""
            thumbnail_url = comic["thumbnail"]?.jsonPrimitive?.content ?: ""
            description = comic["excerpt"]?.jsonPrimitive?.content ?: ""
            status = SManga.UNKNOWN
            initialized = true
        }
    }

    // --- CHAPTERS ---
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = parseReaderData(response)
        val chaptersArray = data["chapters"]?.jsonArray ?: return emptyList()

        return chaptersArray.mapIndexed { index, element ->
            val chap = element.jsonObject
            SChapter.create().apply {
                name = chap["title"]?.jsonPrimitive?.content ?: "Capítulo ${index + 1}"
                url = response.request.url.encodedPath + "#" + chap["id"]?.jsonPrimitive?.content
                chapter_number = (chaptersArray.size - index).toFloat()
            }
        }
    }

    // --- PAGES ---
    override fun pageListRequest(chapter: SChapter): Request {
        val mangaUrl = chapter.url.substringBefore("#")
        return GET(baseUrl + mangaUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = parseReaderData(response)
        val chaptersArray = data["chapters"]?.jsonArray ?: return emptyList()
        val chapterId = response.request.url.fragment

        val chapterObj = chaptersArray.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == chapterId }?.jsonObject
            ?: throw Exception("Capítulo no encontrado")

        val images = chapterObj["images"]?.jsonArray ?: return emptyList()

        return images.mapIndexed { i, element ->
            Page(i, "", element.jsonPrimitive.content)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("No utilizado")
}
