package com.worldtv.core.network

import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import okhttp3.ResponseBody
import retrofit2.Response

/** What a conditional catalog fetch produced. */
sealed interface CatalogFetch<out T> {
    /** Server says nothing changed. Nothing was downloaded. */
    data object NotModified : CatalogFetch<Nothing>

    data class Changed<T>(val etag: String?, val lastModified: String?, val items: Sequence<T>) :
        CatalogFetch<T>

    data class Failed(val code: Int, val message: String) : CatalogFetch<Nothing>
}

/**
 * Streams the large iptv-org JSON arrays.
 *
 * The catalog files total roughly 20 MB. Decoding them into a `List<T>` materialises
 * the whole array plus a full JSON tree, which is several times that in heap — enough
 * to OOM the low-RAM boxes this app targets. `decodeToSequence` reads one element at a
 * time so peak memory is one object plus the parser's buffer, and the caller writes to
 * Room in batches as the sequence is consumed.
 */
@Singleton
class CatalogDownloader @Inject constructor(private val json: Json) {

    /**
     * Turns a Retrofit response into a lazily-decoded sequence.
     *
     * The returned sequence must be consumed inside [use]; the body is still open.
     */
    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> parse(
        response: Response<ResponseBody>,
        body: (CatalogFetch<T>) -> Unit,
    ) {
        when {
            response.code() == HTTP_NOT_MODIFIED -> body(CatalogFetch.NotModified)

            !response.isSuccessful ->
                body(CatalogFetch.Failed(response.code(), response.message()))

            else -> {
                val responseBody = response.body()
                if (responseBody == null) {
                    body(CatalogFetch.Failed(response.code(), "empty body"))
                    return
                }
                responseBody.use { open ->
                    val stream: InputStream = open.byteStream()
                    body(
                        CatalogFetch.Changed(
                            etag = response.headers()["ETag"],
                            lastModified = response.headers()["Last-Modified"],
                            items = json.decodeToSequence<T>(stream),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val HTTP_NOT_MODIFIED = 304

        /** Rows per Room transaction. Large enough to amortise, small enough to stay cheap. */
        const val BATCH_SIZE = 500
    }
}
