package dev.jacobandersen.beacon.webmention.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

private val logger = KotlinLogging.logger {}

/**
 * Gives Webmention failures a consistent JSON error body
 * (`{"error": ..., "error_description": ...}`) instead of Spring Boot's default
 * error document, and provides the 500 response the spec expects when the
 * receiver cannot process a request (section 3.2.3).
 */
@RestControllerAdvice
class WebmentionExceptionHandler {
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun onUnsupportedMediaType(e: HttpMediaTypeNotSupportedException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "invalid_request", "The request content type is not supported")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun onMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<Map<String, Any>> {
        val builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        e.supportedHttpMethods?.let { builder.header(HttpHeaders.ALLOW, it.joinToString(", ") { method -> method.name() }) }
        return builder.body(errorBody("invalid_request", "The HTTP method is not allowed for this endpoint"))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun onMissingParameter(e: MissingServletRequestParameterException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", "The '${e.parameterName}' parameter is required")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun onTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", "The '${e.name}' parameter is invalid")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", "The request body could not be read")

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun onMediaTypeNotAcceptable(e: HttpMediaTypeNotAcceptableException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_ACCEPTABLE, "invalid_request", "No acceptable response content type was requested")

    @ExceptionHandler(NoResourceFoundException::class)
    fun onNoResource(e: NoResourceFoundException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_FOUND, "not_found", "No such endpoint")

    @ExceptionHandler(Exception::class)
    fun onUnexpected(e: Exception): ResponseEntity<Map<String, Any>> {
        logger.error(e) { "Unhandled Webmention request failure" }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred")
    }

    private fun error(
        status: HttpStatus,
        error: String,
        description: String?,
    ): ResponseEntity<Map<String, Any>> = ResponseEntity.status(status).body(errorBody(error, description))

    private fun errorBody(
        error: String,
        description: String?,
    ): Map<String, Any> {
        val body = linkedMapOf<String, Any>("error" to error)
        if (description != null) body["error_description"] = description
        return body
    }
}
