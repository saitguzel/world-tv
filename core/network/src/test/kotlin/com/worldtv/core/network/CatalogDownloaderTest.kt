package com.worldtv.core.network

import com.worldtv.core.network.model.ApiCategory
import kotlinx.serialization.json.Json
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import retrofit2.Response

/**
 * The conditional-fetch branches of [CatalogDownloader.parse].
 *
 * Retrofit responses are built by hand rather than served from a socket: the branches
 * are decided on the status code and headers, and the only thing a server would add is
 * a port to fight over. A 304 in particular can only be built through `Response.error`
 * — Retrofit refuses a non-2xx raw response on the success path — which is also how
 * Retrofit itself delivers one.
 */
class CatalogDownloaderTest {

    private val downloader = CatalogDownloader(Json { ignoreUnknownKeys = true })

    @Test
    fun `a 304 is reported as not modified`() {
        // The whole point of sending the validators is to land here and skip 20 MB.
        val response = Response.error<ResponseBody>("".toResponseBody(JSON), raw(304, "Not Modified"))

        assertEquals(CatalogFetch.NotModified, parse(response))
    }

    @Test
    fun `a server error carries its code and message`() {
        val response = Response.error<ResponseBody>("".toResponseBody(JSON), raw(503, "Service Unavailable"))

        assertEquals(CatalogFetch.Failed(503, "Service Unavailable"), parse(response))
    }

    @Test
    fun `a success with no body is a failure rather than a crash`() {
        val response = Response.success<ResponseBody>(null)

        assertEquals(CatalogFetch.Failed(200, "empty body"), parse(response))
    }

    @Test
    fun `a changed catalog streams its items and keeps the validators`() {
        val body = """[{"id":"news","name":"News"},{"id":"kids"}]""".toResponseBody(JSON)
        val response = Response.success(
            body,
            headersOf("ETag", "\"abc\"", "Last-Modified", "Tue, 01 Sep 2026 00:00:00 GMT"),
        )

        val changed = parse(response) as? CatalogFetch.Changed<ApiCategory>
            ?: fail("expected Changed")

        assertEquals("\"abc\"", changed.etag)
        assertEquals("Tue, 01 Sep 2026 00:00:00 GMT", changed.lastModified)
        assertEquals(listOf(ApiCategory("news", "News"), ApiCategory("kids")), changed.items.toList())
    }

    @Test
    fun `a changed catalog without validators still decodes`() {
        // iptv-org serves through a CDN that has dropped ETag before; the sync must
        // not depend on it, it just loses the 304 shortcut next time.
        val response = Response.success("""[{"id":"sports"}]""".toResponseBody(JSON))

        val changed = parse(response) as? CatalogFetch.Changed<ApiCategory>
            ?: fail("expected Changed")

        assertNull(changed.etag)
        assertNull(changed.lastModified)
        assertEquals(listOf(ApiCategory("sports")), changed.items.toList())
    }

    /**
     * Consumes the sequence inside the callback, as production must: the body is
     * closed the moment `parse` returns, so a sequence read afterwards would throw.
     */
    private fun parse(response: Response<ResponseBody>): CatalogFetch<ApiCategory> {
        lateinit var result: CatalogFetch<ApiCategory>
        downloader.parse<ApiCategory>(response) { fetch ->
            result = when (fetch) {
                is CatalogFetch.Changed -> fetch.copy(items = fetch.items.toList().asSequence())
                else -> fetch
            }
        }
        return result
    }

    private fun raw(code: Int, message: String): okhttp3.Response = okhttp3.Response.Builder()
        .code(code)
        .message(message)
        .protocol(Protocol.HTTP_1_1)
        .request(Request.Builder().url("http://localhost/categories.json").build())
        .build()

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
