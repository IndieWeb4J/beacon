package dev.jacobandersen.beacon.webmention.http

import dev.jacobandersen.beacon.config.WebmentionProperties
import dev.jacobandersen.beacon.util.HttpUtil
import dev.jacobandersen.beacon.util.HttpUtil.isTransientStatus
import dev.jacobandersen.beacon.util.StringUtil.excerpt
import dev.jacobandersen.beacon.util.WebmentionUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

private val logger = KotlinLogging.logger {}

sealed interface SendWebmentionResult {
    data class Success(
        val statusCode: Int,
    ) : SendWebmentionResult

    data class Failure(
        val statusCode: Int?,
        val message: String,
        val retryable: Boolean,
    ) : SendWebmentionResult
}

data class EndpointDiscovery(
    val endpointUrl: String?,
    val cacheControl: String?,
    val expiresHeader: String?,
)

@Service
class WebmentionHttpClient(
    private val config: WebmentionProperties,
    private val hostValidator: SourceHostValidator,
) {
    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(requestFactory(config))
            .requestInterceptor(WebmentionHttpLoggingInterceptor())
            .build()

    private fun requestFactory(config: WebmentionProperties): ClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds))
                .build()

        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds))
        }
    }

    internal fun sendWebmention(
        sourceUrl: String,
        targetUrl: String,
        endpointUrl: String,
    ): SendWebmentionResult {
        val payload = LinkedMultiValueMap<String, String>()
        payload.add("source", sourceUrl)
        payload.add("target", targetUrl)

        return try {
            val statusCode =
                client
                    .post()
                    .uri(endpointUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .statusCode
                    .value()

            if (statusCode in 200..299) {
                SendWebmentionResult.Success(statusCode)
            } else {
                SendWebmentionResult.Failure(statusCode, "HTTP $statusCode", isTransientStatus(statusCode))
            }
        } catch (e: RestClientResponseException) {
            val statusCode = e.statusCode.value()
            val message = describeHttpError(statusCode, e.responseBodyAsString)
            SendWebmentionResult.Failure(statusCode, message, isTransientStatus(statusCode))
        } catch (e: RestClientException) {
            SendWebmentionResult.Failure(null, e.message ?: e::class.simpleName ?: "Request failed", true)
        }
    }

    private fun describeHttpError(
        statusCode: Int,
        responseBody: String?,
    ): String {
        val body = responseBody?.takeIf { it.isNotBlank() }?.excerpt(MAX_ERROR_BODY_LENGTH)
        return if (body != null) "HTTP $statusCode: $body" else "HTTP $statusCode"
    }

    internal fun discoverWebmentionEndpoint(url: String): EndpointDiscovery {
        logger.info { "Discovering webmention endpoint for $url" }
        return runCatching { discoverFollowingRedirects(url) }
            .getOrElse {
                logger.warn { "Webmention endpoint discovery failed for $url: ${it.message}" }
                EndpointDiscovery(null, null, null)
            }
    }

    /**
     * Follows redirects manually so the host of every hop is validated by the
     * [SourceHostValidator]; blindly following redirects would let a public URL
     * redirect discovery to an internal address (SSRF). The document body is
     * size-capped like the source fetcher.
     */
    private fun discoverFollowingRedirects(startUrl: String): EndpointDiscovery {
        var current = startUrl
        repeat(MAX_REDIRECTS) {
            if (hostValidator.isBlocked(current)) {
                logger.warn { "Refusing to discover a webmention endpoint at a blocked host: $current" }
                return EndpointDiscovery(null, null, null)
            }

            val response =
                Jsoup
                    .connect(current)
                    .userAgent(USER_AGENT)
                    .header(HttpHeaders.ACCEPT, DISCOVERY_ACCEPT)
                    .followRedirects(false)
                    .ignoreHttpErrors(true)
                    .maxBodySize(MAX_BODY_SIZE)
                    .timeout((config.readTimeoutSeconds * 1000).toInt())
                    .execute()

            val status = response.statusCode()
            if (status in REDIRECT_STATUSES) {
                val location = response.header("Location")?.trim().orEmpty()
                if (location.isEmpty()) return EndpointDiscovery(null, null, null)
                current =
                    runCatching { URI(current).resolve(location).toString() }.getOrNull()
                        ?: return EndpointDiscovery(null, null, null)
                return@repeat
            }

            val cacheControl = firstHeader(response, "Cache-Control")
            val expires = firstHeader(response, "Expires")
            val baseUrl = response.url().toExternalForm()

            val linkEndpoint =
                WebmentionUtil
                    .findEndpointInLinkHeaders(response.headers(HttpHeaders.LINK))
                    ?.let { WebmentionUtil.resolveEndpoint(it, baseUrl) }

            if (linkEndpoint != null) {
                logger.info { "Found valid HTTP Link: $linkEndpoint" }
                return EndpointDiscovery(linkEndpoint, cacheControl, expires)
            }

            if (!HttpUtil.isHtmlContentType(response.contentType())) {
                logger.info { "No valid HTTP Link found, and document is not HTML, cannot resolve webmention endpoint" }
                return EndpointDiscovery(null, cacheControl, expires)
            }

            val htmlEndpoint =
                response
                    .parse()
                    .select("link[href], a[href]")
                    .firstOrNull { element ->
                        WebmentionUtil.hasWebmentionRel(element.attr("rel")) && element.absUrl("href").isNotBlank()
                    }?.absUrl("href")

            if (htmlEndpoint != null) {
                logger.info { "Found valid HTML endpoint: $htmlEndpoint" }
                return EndpointDiscovery(htmlEndpoint, cacheControl, expires)
            }

            logger.info { "Could not resolve webmention endpoint for $startUrl" }
            return EndpointDiscovery(null, cacheControl, expires)
        }
        return EndpointDiscovery(null, null, null)
    }

    private fun firstHeader(
        res: org.jsoup.Connection.Response,
        name: String,
    ): String? = res.headers(name).firstOrNull()

    companion object {
        const val USER_AGENT = "BeaconWebmentionHttpClient/0.0.1"
        const val MAX_ERROR_BODY_LENGTH = 2000
        const val MAX_REDIRECTS = 10
        const val MAX_BODY_SIZE = 1_000_000
        const val DISCOVERY_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
