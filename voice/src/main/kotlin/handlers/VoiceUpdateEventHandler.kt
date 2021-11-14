package dev.kord.voice.handlers

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.DiscordVoicePacketData
import dev.kord.common.entity.DiscordVoiceServerUpdateData
import dev.kord.common.entity.DiscordVoiceState
import dev.kord.voice.DefaultVoiceConnection
import dev.kord.voice.VoiceConnection
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val voiceUpdateLogger = KotlinLogging.logger { }

/**
 * This is how long we will wait to see if a disconnect resulted in a reconnect.
 * If it didn't, we should detach the connection.
 */
private const val CONNECTION_DETACH_DURATION_IN_MILLIS: Long = 5000L

@KordVoice
internal class VoiceUpdateEventHandler(
    flow: Flow<DiscordVoicePacketData>,
    private val connection: DefaultVoiceConnection,
) : ConnectionEventHandler<DiscordVoicePacketData>(flow, "VoiceUpdateInterceptor") {
    private var detachJob: Job? by atomic(null)

    override suspend fun start() = coroutineScope {
        on<DiscordVoiceState> { event ->
            if (event.userId != connection.data.selfId || event.isRelatedToConnection(connection)) return@on

            // we're not in a voice channel anymore. anything might've happened
            // discord doesn't tell us whether the channel was deleted or if we were just moved
            // let's just detach in 5 seconds and let it be cancelled if we join a new voice channel in that time.
            if (event.channelId == null) {
                detachJob?.cancel()
                detachJob = launch {
                    delay(CONNECTION_DETACH_DURATION_IN_MILLIS)

                    connection.shutdown()
                }
            } else if (event.channelId != null) {
                detachJob?.cancel()
                detachJob = null
            }
        }

        on<DiscordVoiceServerUpdateData> { voiceServerUpdate ->
            if (!voiceServerUpdate.isRelatedToConnection(connection)) return@on

            // voice server has gone away.
            if (voiceServerUpdate.endpoint == null) {
                connection.disconnect()
            }

            voiceUpdateLogger.trace { "changing voice servers for session ${connection.data.sessionId}" }

            // update the gateway configuration accordingly
            connection.voiceGatewayConfiguration = connection.voiceGatewayConfiguration?.copy(
                token = voiceServerUpdate.token,
                endpoint = "wss://${voiceServerUpdate.endpoint}?v=4"
            )

            // reconnect...
            connection.disconnect()
            connection.connect()
        }
    }
}

@KordVoice
private fun DiscordVoiceServerUpdateData.isRelatedToConnection(connection: VoiceConnection): Boolean =
    guildId == connection.data.guildId

@KordVoice
private fun DiscordVoiceState.isRelatedToConnection(connection: VoiceConnection): Boolean =
    guildId.value == connection.data.guildId
