package dev.jacobandersen.beacon.event

import dev.jacobandersen.beacon.webmention.service.WebmentionService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

class ContentEventDispatcherTest {
    private val postId: UUID = UUID.randomUUID()
    private val url = "https://example.com/2024/01/01/s"

    private val webmentionService: WebmentionService = mock()
    private val checkpointService: ContentEventCheckpointService = mock()
    private val dispatcher =
        ContentEventDispatcher(
            objectMapper = jacksonObjectMapper(),
            webmentionService = webmentionService,
            checkpointService = checkpointService,
        )

    private fun createdJson(version: Int): String =
        """
        {
          "eventType": "CREATED",
          "id": "$postId",
          "slug": "s",
          "url": "$url",
          "h": "h-entry",
          "status": "PUBLISHED",
          "visibility": "PUBLIC",
          "deleted": false,
          "categories": [],
          "version": $version,
          "post": {"type": ["h-entry"], "properties": {"content": ["hi https://other.example/a"]}},
          "syndicationTargets": []
        }
        """.trimIndent()

    @Test
    fun `applies a created event and records the checkpoint`() {
        given(checkpointService.lastApplied(postId)).willReturn(0)

        dispatcher.handle(createdJson(version = 1))

        verify(webmentionService).reconcile(eq(url), any(), eq(true))
        verify(checkpointService).record(postId, 1)
    }

    @Test
    fun `ignores a stale event`() {
        given(checkpointService.lastApplied(postId)).willReturn(5)

        dispatcher.handle(createdJson(version = 3))

        verify(webmentionService, never()).reconcile(any(), any(), any())
        verify(checkpointService, never()).record(any(), any())
    }
}
