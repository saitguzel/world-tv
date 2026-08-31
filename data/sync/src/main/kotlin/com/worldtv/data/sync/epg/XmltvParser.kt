package com.worldtv.data.sync.epg

import com.worldtv.core.model.Programme
import com.worldtv.core.model.XmltvTime
import java.io.InputStream
import javax.inject.Inject
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * Streaming XMLTV reader.
 *
 * Programmes are pushed through a callback rather than returned as a list: a national
 * guide covering a fortnight runs to tens of megabytes and hundreds of thousands of
 * `<programme>` elements, and materialising that costs more heap than a TV box has.
 * The caller batches into Room as programmes arrive.
 *
 * Written against SAX rather than `android.util.Xml` so it stays plain JVM code and
 * can be tested without an emulator — this is the part of the EPG pipeline most likely
 * to be wrong, and the hardest to debug on a device.
 *
 * Malformed entries are skipped rather than aborting the document. Public guides are
 * of wildly uneven quality, and losing one programme is much better than losing a
 * whole country's schedule to one bad timestamp.
 */
class XmltvParser @Inject constructor() {

    data class Stats(val parsed: Int, val skipped: Int)

    /**
     * @param channelIdFilter true for channels worth keeping. Guides carry schedules
     *   for channels the catalog does not have, and storing those wastes space the
     *   device does not have either.
     * @param onProgramme called for each usable programme, in document order
     */
    fun parse(
        input: InputStream,
        channelIdFilter: (String) -> Boolean = { true },
        onProgramme: (Programme) -> Unit,
    ): Stats {
        val handler = ProgrammeHandler(channelIdFilter, onProgramme)
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            // XMLTV files are fetched from third-party URLs, so external entity
            // resolution must be off: it is the classic XXE vector and no legitimate
            // guide needs it.
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
        }

        try {
            factory.newSAXParser().parse(InputSource(input), handler)
        } catch (e: SAXException) {
            // A truncated download leaves a half-written tag. Everything read up to
            // that point is valid and already delivered.
        }
        return Stats(handler.parsed, handler.skipped)
    }
}

private class ProgrammeHandler(
    private val channelIdFilter: (String) -> Boolean,
    private val onProgramme: (Programme) -> Unit,
) : DefaultHandler() {

    var parsed = 0
        private set
    var skipped = 0
        private set

    private var channelId: String? = null
    private var startAt: Long? = null
    private var endAt: Long? = null
    private var wanted = false

    private var title: String? = null
    private var description: String? = null
    private var category: String? = null
    private var episode: String? = null

    /** Non-null while inside a leaf whose text we want. */
    private var capturing: StringBuilder? = null

    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        when (qName) {
            TAG_PROGRAMME -> {
                channelId = attributes.getValue(ATTR_CHANNEL)
                startAt = attributes.getValue(ATTR_START)?.let(XmltvTime::parse)
                endAt = attributes.getValue(ATTR_STOP)?.let(XmltvTime::parse)
                title = null
                description = null
                category = null
                episode = null
                val id = channelId
                wanted = id != null &&
                    channelIdFilter(id) &&
                    startAt != null &&
                    endAt != null &&
                    // A zero or negative duration is a corrupt entry, and it would
                    // make every "what is on now" query ambiguous.
                    endAt!! > startAt!!
            }

            // Only capture text for the first occurrence of each field: guides repeat
            // these once per language and the first is conventionally the primary one.
            TAG_TITLE -> if (wanted && title == null) capturing = StringBuilder()
            TAG_DESC -> if (wanted && description == null) capturing = StringBuilder()
            TAG_CATEGORY -> if (wanted && category == null) capturing = StringBuilder()
            TAG_EPISODE_NUM -> if (wanted && episode == null) capturing = StringBuilder()
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        capturing?.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        when (qName) {
            TAG_TITLE -> title = takeCaptured() ?: title
            TAG_DESC -> description = takeCaptured() ?: description
            TAG_CATEGORY -> category = takeCaptured() ?: category
            TAG_EPISODE_NUM -> episode = takeCaptured() ?: episode

            TAG_PROGRAMME -> {
                val cleanTitle = title?.trim().orEmpty()
                if (wanted && cleanTitle.isNotEmpty()) {
                    parsed++
                    onProgramme(
                        Programme(
                            channelId = channelId!!,
                            startAt = startAt!!,
                            endAt = endAt!!,
                            title = cleanTitle,
                            description = description.cleaned(),
                            category = category.cleaned(),
                            episode = episode.cleaned(),
                        ),
                    )
                } else {
                    skipped++
                }
                wanted = false
                capturing = null
            }
        }
    }

    private fun takeCaptured(): String? {
        val captured = capturing?.toString()
        capturing = null
        return captured
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG_PROGRAMME = "programme"
        const val TAG_TITLE = "title"
        const val TAG_DESC = "desc"
        const val TAG_CATEGORY = "category"
        const val TAG_EPISODE_NUM = "episode-num"
        const val ATTR_CHANNEL = "channel"
        const val ATTR_START = "start"
        const val ATTR_STOP = "stop"
    }
}
