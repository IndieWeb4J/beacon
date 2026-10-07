package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.webmention.http.SourceFetch
import dev.jacobandersen.microformats2.Mf2ParserImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WebmentionSourceVerifierTest {
    private val parser = Mf2ParserImpl()
    private val target = "https://example.com/2026/10/07/hello"

    private fun fetch(
        status: Int,
        contentType: String? = "text/html",
        body: String = "",
    ): SourceFetch = SourceFetch(statusCode = status, finalUrl = "https://source.example/post", contentType = contentType, body = body)

    @Test
    fun `410 marks the source gone`() {
        assertEquals(SourceVerdict.GONE, WebmentionSourceVerifier.verify(fetch(410), target, parser).verdict)
    }

    @Test
    fun `404 is unreachable rather than gone`() {
        assertEquals(SourceVerdict.UNREACHABLE, WebmentionSourceVerifier.verify(fetch(404), target, parser).verdict)
    }

    @Test
    fun `html without the target is no link`() {
        val body = """<html><body><a href="https://other.example/">x</a></body></html>"""
        assertEquals(SourceVerdict.NO_LINK, WebmentionSourceVerifier.verify(fetch(200, body = body), target, parser).verdict)
    }

    @Test
    fun `html linking the target is verified`() {
        val body = """<html><body><a href="$target">x</a></body></html>"""
        assertEquals(SourceVerdict.VERIFIED, WebmentionSourceVerifier.verify(fetch(200, body = body), target, parser).verdict)
    }
}
