package dev.jacobandersen.beacon.reconciliation

import dev.jacobandersen.beacon.config.ReconciliationProperties
import dev.jacobandersen.beacon.webmention.service.WebmentionService
import dev.jacobandersen.content.client.ChangedPostsPage
import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class BeaconReconciliationServiceTest {
    private val client: ContentReadClient = mock()
    private val provider: ObjectProvider<ContentReadClient> = mock()
    private val webmentionService: WebmentionService = mock()
    private val cursorService: ReconciliationCursorService = mock()
    private val properties = ReconciliationProperties(pageSize = 100, maxPages = 10)

    private val service =
        BeaconReconciliationService(provider, webmentionService, cursorService, properties)

    private fun post(
        slug: String,
        status: String = "PUBLISHED",
        visibility: String = "PUBLIC",
        deleted: Boolean = false,
    ): PostDto =
        PostDto(
            id = slug,
            slug = slug,
            url = "https://example.com/2024/01/01/$slug",
            h = "h-entry",
            status = status,
            visibility = visibility,
            deleted = deleted,
            categories = emptyList(),
            version = 1,
            post = Mf2Object(type = listOf("h-entry"), properties = mapOf("content" to listOf(Mf2Value.String(slug)))),
        )

    @Test
    fun `reconciles public posts and retracts non-public ones`() {
        whenever(provider.ifAvailable).thenReturn(client)
        val publicPost = post("public")
        val draft = post("draft", status = "DRAFT")
        whenever(client.changedSince(null, 100)).thenReturn(ChangedPostsPage(listOf(publicPost, draft), null))
        whenever(cursorService.get(BeaconReconciliationService.CURSOR_ID)).thenReturn(null)

        service.reconcile()

        verify(webmentionService).reconcile(eq(publicPost.url), eq(publicPost.post), eq(false))
        verify(webmentionService).retract(draft.url)
    }

    @Test
    fun `advances the persisted cursor across pages`() {
        whenever(provider.ifAvailable).thenReturn(client)
        whenever(cursorService.get(BeaconReconciliationService.CURSOR_ID)).thenReturn(null)
        whenever(client.changedSince(null, 100))
            .thenReturn(ChangedPostsPage(listOf(post("one")), nextCursor = "100"))
        whenever(client.changedSince("100", 100))
            .thenReturn(ChangedPostsPage(listOf(post("two")), nextCursor = null))

        service.reconcile()

        verify(cursorService).set(BeaconReconciliationService.CURSOR_ID, "100")
        verify(webmentionService, org.mockito.kotlin.times(2)).reconcile(org.mockito.kotlin.any(), org.mockito.kotlin.any(), eq(false))
    }

    @Test
    fun `does nothing when the content read client is not configured`() {
        whenever(provider.ifAvailable).thenReturn(null)

        service.reconcile()

        verify(client, never()).changedSince(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(webmentionService, never()).reconcile(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
