package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.event.WebmentionEvent
import dev.jacobandersen.beacon.event.WebmentionEventPublisher
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmention
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionState
import dev.jacobandersen.beacon.webmention.http.SourceFetch
import dev.jacobandersen.beacon.webmention.http.WebmentionSourceFetcher
import dev.jacobandersen.microformats2.Mf2ParserImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

class WebmentionReceiverServiceTest {
    private val receivedService: ReceivedWebmentionService = mock()
    private val sourceFetcher: WebmentionSourceFetcher = mock()
    private val eventPublisher: WebmentionEventPublisher = mock()
    private val service = WebmentionReceiverService(receivedService, sourceFetcher, Mf2ParserImpl(), eventPublisher)

    private val source = "https://source.example/post"
    private val target = "https://example.com/2026/10/07/hello"
    private val postId: UUID = UUID.randomUUID()

    private fun record(state: ReceivedWebmentionState): ReceivedWebmention =
        ReceivedWebmention(
            id = UUID.randomUUID(),
            postId = postId,
            sourceUrl = source,
            targetUrl = target,
            state = state,
            interaction = null,
            authorName = null,
            authorUrl = null,
            authorPhoto = null,
            contentText = emptyList(),
            contentHtml = emptyList(),
            rawMf2 = null,
            lastError = null,
            firstSeenAt = Instant.now(),
            verifiedAt = null,
            updatedAtUtc = Instant.now(),
        )

    private fun fetch(
        status: Int,
        body: String = "",
    ): SourceFetch = SourceFetch(statusCode = status, finalUrl = source, contentType = "text/html", body = body)

    @Test
    fun `verified stores the mention and emits verified`() {
        given(receivedService.notification(source, postId)).willReturn(null)
        given(sourceFetcher.fetch(source)).willReturn(fetch(200, """<a href="$target">x</a>"""))
        given(receivedService.markVerified(any(), any(), any(), any())).willReturn(record(ReceivedWebmentionState.VERIFIED))

        service.verify(source, target, postId)

        verify(receivedService).markVerified(any(), any(), any(), any())
        verify(eventPublisher).publish(any<WebmentionEvent>())
    }

    @Test
    fun `no link on a new mention stores nothing`() {
        given(receivedService.notification(source, postId)).willReturn(null)
        given(sourceFetcher.fetch(source)).willReturn(fetch(200, "<html><body>no link here</body></html>"))

        service.verify(source, target, postId)

        verify(receivedService, never()).markVerified(any(), any(), any(), any())
        verify(receivedService, never()).markDeleted(any(), any())
        verify(eventPublisher, never()).publish(any<WebmentionEvent>())
    }

    @Test
    fun `gone for a previously verified mention retracts it`() {
        given(receivedService.notification(source, postId)).willReturn(record(ReceivedWebmentionState.VERIFIED))
        given(sourceFetcher.fetch(source)).willReturn(fetch(410))
        given(receivedService.markDeleted(source, postId)).willReturn(record(ReceivedWebmentionState.DELETED))

        service.verify(source, target, postId)

        verify(receivedService).markDeleted(source, postId)
        verify(eventPublisher).publish(any<WebmentionEvent>())
    }

    @Test
    fun `unreachable stores nothing and leaves an existing mention untouched`() {
        given(receivedService.notification(source, postId)).willReturn(record(ReceivedWebmentionState.VERIFIED))
        given(sourceFetcher.fetch(source)).willReturn(fetch(503))

        service.verify(source, target, postId)

        verify(receivedService, never()).markVerified(any(), any(), any(), any())
        verify(receivedService, never()).markDeleted(any(), any())
        verify(eventPublisher, never()).publish(any<WebmentionEvent>())
    }
}
