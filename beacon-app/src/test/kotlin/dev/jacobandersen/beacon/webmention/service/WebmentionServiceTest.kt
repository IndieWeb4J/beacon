package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.config.BeaconContentProperties
import dev.jacobandersen.beacon.config.WebmentionProperties
import dev.jacobandersen.beacon.url.ContentUrlService
import dev.jacobandersen.beacon.webmention.http.WebmentionHttpClient
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

class WebmentionServiceTest {
    private val jobScheduler: JobScheduler = mock()
    private val notificationService: WebmentionNotificationService = mock()
    private val endpointCacheService: WebmentionEndpointCacheService = mock()
    private val httpClient: WebmentionHttpClient = mock()
    private val contentUrlService =
        ContentUrlService(BeaconContentProperties(baseUrl = "https://example.com", pathPattern = "{year}/{month}/{day}/{slug}"))

    private val service =
        WebmentionService(
            jobScheduler = jobScheduler,
            notificationService = notificationService,
            endpointCacheService = endpointCacheService,
            httpClient = httpClient,
            config = WebmentionProperties(),
            contentUrlService = contentUrlService,
        )

    private fun entryWith(vararg content: String): Mf2Object =
        Mf2Object(
            type = listOf("h-entry"),
            properties = mapOf("content" to content.map { Mf2Value.String(it) }),
        )

    @Test
    fun `target urls exclude own content and deduplicate`() {
        val post =
            entryWith(
                "hello https://other.example/a and https://example.com/2024/01/01/x and https://other.example/a",
            )
        assertEquals(setOf("https://other.example/a"), service.targetUrlsOf(post))
    }

    @Test
    fun `reconcile sends newly referenced targets`() {
        val source = "https://example.com/2024/01/01/my-post"
        given(notificationService.activeNotificationsBySource(source)).willReturn(emptyList())

        service.reconcile(source, entryWith("see https://other.example/a"))

        verify(notificationService).setActivePending(source, "https://other.example/a")
    }

    @Test
    fun `reconcile does not send for own content only`() {
        val source = "https://example.com/2024/01/01/my-post"
        given(notificationService.activeNotificationsBySource(source)).willReturn(emptyList())

        service.reconcile(source, entryWith("self https://example.com/2024/01/02/other"))

        verify(notificationService, never()).setActivePending(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
