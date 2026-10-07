package dev.jacobandersen.beacon.webmention.controller

import dev.jacobandersen.beacon.url.ContentUrlService
import dev.jacobandersen.beacon.webmention.http.SourceHostValidator
import dev.jacobandersen.beacon.webmention.service.SubmissionDecision
import dev.jacobandersen.beacon.webmention.service.WebmentionReceiverService
import dev.jacobandersen.beacon.webmention.service.WebmentionSubmissionLimiter
import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.microformats2.Mf2Object
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@WebMvcTest(WebmentionController::class)
@AutoConfigureMockMvc(addFilters = false)
class WebmentionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var receiverService: WebmentionReceiverService

    @MockitoBean
    private lateinit var contentReadClient: ContentReadClient

    @MockitoBean
    private lateinit var contentUrlService: ContentUrlService

    @MockitoBean
    private lateinit var jobScheduler: JobScheduler

    @MockitoBean
    private lateinit var hostValidator: SourceHostValidator

    @MockitoBean
    private lateinit var submissionLimiter: WebmentionSubmissionLimiter

    private val source = "https://source.example/post"
    private val target = "https://example.com/2026/10/07/hello"

    private fun publicPost(): PostDto =
        PostDto(
            id = "11111111-1111-1111-1111-111111111111",
            slug = "hello",
            url = target,
            h = "h-entry",
            type = "note",
            status = "PUBLISHED",
            visibility = "PUBLIC",
            deleted = false,
            categories = emptyList(),
            version = 1,
            post = Mf2Object(type = listOf("h-entry"), properties = emptyMap()),
        )

    private fun stubAcceptableSource() {
        given(hostValidator.isBlocked(any())).willReturn(false)
        given(contentUrlService.isOwnContentUrl(any())).willReturn(false)
        given(submissionLimiter.allow(any(), any(), any())).willReturn(SubmissionDecision.Allowed)
    }

    @Test
    fun `accepts a valid webmention with 202`() {
        stubAcceptableSource()
        given(contentUrlService.extractPostSlug(target)).willReturn("hello")
        given(contentReadClient.postBySlug("hello")).willReturn(publicPost())

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", source)
                    .param("target", target),
            ).andExpect(status().isAccepted)
    }

    @Test
    fun `rejects a missing source with 400`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("target", target),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }

    @Test
    fun `returns 429 with Retry-After when the source is over its limit`() {
        stubAcceptableSource()
        given(submissionLimiter.allow(any(), any(), any()))
            .willReturn(SubmissionDecision.RateLimited(Duration.ofSeconds(30)))

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", source)
                    .param("target", target),
            ).andExpect(status().isTooManyRequests)
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
            .andExpect(jsonPath("$.error").value("rate_limited"))
    }

    @Test
    fun `rejects a target that is not a public post`() {
        stubAcceptableSource()
        given(contentUrlService.extractPostSlug(any())).willReturn("hello")
        given(contentReadClient.postBySlug(any())).willReturn(publicPost().copy(deleted = true))

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", source)
                    .param("target", target),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }

    @Test
    fun `rejects an unsupported content type with the JSON error shape`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }
}
