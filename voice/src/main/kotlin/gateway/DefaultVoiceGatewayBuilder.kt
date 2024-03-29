package dev.kord.voice.gateway

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.common.retry.LinearRetry
import dev.kord.common.retry.Retry
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration

@KordVoice
class DefaultVoiceGatewayBuilder(
    val selfId: Snowflake,
    val guildId: Snowflake,
    val sessionId: String,
) {
    var client: HttpClient? = null
    var reconnectRetry: Retry? = null
    var eventFlow: MutableSharedFlow<VoiceEvent> = MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE)

    @OptIn(InternalAPI::class)
    fun build(): DefaultVoiceGateway {
        val client = client ?: HttpClient(CIO) {
            install(WebSockets)
            install(JsonFeature)
        }

        val retry = reconnectRetry ?: LinearRetry(Duration.seconds(2), Duration.seconds(20), 10)

        client.requestPipeline.intercept(HttpRequestPipeline.Render) {
            // CIO adds this header even if no extensions are used, which causes it to be empty
            // This immediately kills the gateway connection
            if (context.url.protocol.isWebsocket()) {
                val header = context.headers[HttpHeaders.SecWebSocketExtensions]
                // If it's blank Discord rage quits
                if (header?.isBlank() == true) {
                    context.headers.remove(HttpHeaders.SecWebSocketExtensions)
                }
            }

            proceed()
        }

        val data = DefaultVoiceGatewayData(
            selfId,
            guildId,
            sessionId,
            client,
            retry,
            eventFlow
        )

        return DefaultVoiceGateway(data)
    }
}
