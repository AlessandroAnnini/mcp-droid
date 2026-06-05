package com.alessandroannini.mcpdroid.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respond

/**
 * Installs a bearer-token gate on all /mcp requests.
 * Requests without a matching Authorization header receive a 401 immediately.
 */
fun Application.installBearerAuth(token: String) {
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        if (path.startsWith("/mcp")) {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader != "Bearer $token") {
                call.respond(HttpStatusCode.Unauthorized)
                finish()
            }
        }
    }
}
