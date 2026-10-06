package dev.jacobandersen.beacon.webmention

import dev.jacobandersen.content.client.ContentReadClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.MediaType

private val logger = KotlinLogging.logger {}

/**
 * Webmention receiver. Verifies the target is a public post on the content
 * service (via [ContentReadClient]) and forwards the mention to the target's
 * webmention endpoint. Sending is delegated to a future [WebmentionSendService].
 */
@RestController
@RequestMapping("/webmention")
class WebmentionController(
    private val contentReadClient: ContentReadClient,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun receive(
        @RequestPart("source") source: String,
        @RequestPart("target") target: String,
        @RequestPart("file", required = false) file: MultipartFile?,
    ): ResponseEntity<*> {
        if (!contentReadClient.isPublicPost(target)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "target is not a public post"))
        }
        logger.info { "Webmention received from $source to $target" }
        return ResponseEntity.accepted().build<Void>()
    }
}
