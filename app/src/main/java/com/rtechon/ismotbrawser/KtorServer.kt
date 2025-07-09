package com.rtechon.ismotbrawser.server

import android.os.Environment
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.default
import io.ktor.server.http.content.files
import io.ktor.server.http.content.static
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File

object KtorServer {
    private var server: ApplicationEngine? = null

    fun start(port: Int = 8080) {
        if (server != null) return

        val rootPath = File(Environment.getExternalStorageDirectory(), "ismot_brawser").absolutePath
        val rootDir = File(rootPath)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
            Log.w("KtorServer", "Created directory: $rootPath")
        }

        server = embeddedServer(CIO, port = port) {
            routing {
                get("/") {
                    call.respondText(
                        """
                        <html>
                          <head><title>ISMOT Brawser - Ktor</title></head>
                          <body>
                            <h2>Ktor is running on Android!</h2>
                            <p>Serving from: <code>$rootPath</code></p>
                            <p>Try accessing: <a href="/index.html">/index.html</a> (if exists)</p>
                          </body>
                        </html>
                        """.trimIndent(),
                        ContentType.Text.Html
                    )
                }

                get("/api/hello") {
                    call.respondText("Hello from Ktor API!", ContentType.Text.Plain)
                }

                post("/api/echo") {
                    val text = call.receiveText()
                    call.respondText("You said: $text", ContentType.Text.Plain)
                }

                // ✅ Serve static files
                static("/") {
                    files(rootDir)
                    default("index.html")
                }
            }
        }.start(wait = false)

        Log.i("KtorServer", "Server started on port $port, serving from $rootPath")
    }

    fun stop() {
        server?.stop(1000, 10000)
        server = null
        Log.i("KtorServer", "Server stopped")
    }
}
