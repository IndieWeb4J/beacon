package dev.jacobandersen.beacon.webmention.controller

import dev.jacobandersen.beacon.url.ContentUrlService
import dev.jacobandersen.beacon.webmention.http.SourceHostValidator
import dev.jacobandersen.beacon.webmention.service.SubmissionDecision
import dev.jacobandersen.beacon.webmention.service.WebmentionReceiverService
import dev.jacobandersen.beacon.webmention.service.WebmentionSubmissionLimiter
import dev.jacobandersen.content.client.ContentReadClient
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.jobrunr.scheduling.JobScheduler
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartHttpServletRequest
import java.net.URI
import java.time.Duration
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * The Webmention receiver endpoint (section 3.2 of the Webmention
 * specification). Performs synchronous request verification and returns 202,
 * deferring source verification to an asynchronous job. The target must be a
 * currently-public post, confirmed through the content service's read API.
 *
 * Rejections are 400 (bad request), 429 (flood control, with `Retry-After`), or
 * 415 (wrong content type); the response body is JSON.
 */
@RestController
@RequestMapping("/webmention")
class WebmentionController(
    private val receiverService: WebmentionReceiverService,
    private val contentReadClient: ContentReadClient,
    private val contentUrlService: ContentUrlService,
    private val jobScheduler: JobScheduler,
    private val hostValidator: SourceHostValidator,
    private val submissionLimiter: WebmentionSubmissionLimiter,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun onUrlEncoded(request: HttpServletRequest): ResponseEntity<*> = handle(request.parameterMap)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun onMultipart(request: MultipartHttpServletRequest): ResponseEntity<*> = handle(request.parameterMap)

    private fun handle(params: Map<String, Array<String>>): ResponseEntity<*> {
        val sourceUrl = firstParam(params, "source")
        val targetUrl = firstParam(params, "target")

        if (sourceUrl == null) return invalidRequest("The source parameter is required")
        if (targetUrl == null) return invalidRequest("The target parameter is required")
        if (!isHttpUrl(sourceUrl)) return invalidRequest("The source URL is not a valid http(s) URL")
        if (!isHttpUrl(targetUrl)) return invalidRequest("The target URL is not a valid http(s) URL")
        if (sourceUrl == targetUrl) return invalidRequest("The source and target URLs must be different")
        if (contentUrlService.isOwnContentUrl(sourceUrl)) return invalidRequest("Self webmentions are not accepted")
        if (hostValidator.isBlocked(sourceUrl)) return invalidRequest("The source URL host is not reachable")
        when (val decision = submissionLimiter.allow(sourceUrl, targetUrl)) {
            is SubmissionDecision.Allowed -> Unit
            is SubmissionDecision.RateLimited -> return tooManyRequests(decision.retryAfter)
        }

        val slug = contentUrlService.extractPostSlug(targetUrl)
        val post = slug?.let { contentReadClient.postBySlug(it) }
        if (post == null || post.deleted || post.status != "PUBLISHED" || post.visibility != "PUBLIC") {
            return invalidRequest("The target URL does not accept webmentions")
        }

        val postId = UUID.fromString(post.id)
        jobScheduler.enqueue { receiverService.verify(sourceUrl, targetUrl, postId) }

        logger.info { "Accepted webmention from $sourceUrl for $targetUrl" }
        return ResponseEntity.accepted().build<Void>()
    }

    private fun firstParam(
        params: Map<String, Array<String>>,
        name: String,
    ): String? = params[name]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun isHttpUrl(value: String): Boolean =
        runCatching {
            val uri = URI(value)
            uri.isAbsolute && (uri.scheme == "http" || uri.scheme == "https") && uri.host != null
        }.getOrDefault(false)

    private fun invalidRequest(description: String): ResponseEntity<*> = jsonError(HttpStatus.BAD_REQUEST, "invalid_request", description)

    private fun tooManyRequests(retryAfter: Duration): ResponseEntity<*> =
        ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, retryAfter.seconds.coerceAtLeast(1L).toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "error" to "rate_limited",
                    "error_description" to "Too many recent webmentions from this source",
                ),
            )

    private fun jsonError(
        status: HttpStatus,
        error: String,
        description: String,
    ): ResponseEntity<*> =
        ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "error" to error,
                    "error_description" to description,
                ),
            )
}
