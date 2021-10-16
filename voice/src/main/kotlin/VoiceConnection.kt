@file:OptIn(KordVoice::class)

package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.voice.gateway.VoiceGateway
import dev.kord.voice.gateway.VoiceGatewayConfiguration
import dev.kord.voice.streams.Streams
import dev.kord.voice.udp.AudioFramePoller
import dev.kord.voice.udp.VoiceUdpSocket
import io.ktor.util.network.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Data that represents a [DefaultVoiceConnection], these will never change during the life time of a [DefaultVoiceConnection].
 *
 * @param selfId the id of the bot connecting to a voice channel.
 * @param guildId the id of the guild that the bot is connecting to.
 * @param sessionId the id of the current voice session, given by Discord.
 */
data class VoiceConnectionData(
    val selfId: Snowflake,
    val guildId: Snowflake,
    val sessionId: String
)

/**
 * A [VoiceConnection] is an adapter that forms the concept of a voice connection, or a connection between you and Discord voice servers.
 */
@KordVoice
interface VoiceConnection {
    val data: VoiceConnectionData

    /**
     * The voice server we're connected to, or null if we're not connected to one.
     */
    val voiceServer: NetworkAddress?

    /**
     * The underlying [VoiceGateway] for this voice channel.
     */
    val voiceGateway: VoiceGateway

    /**
     * The udp socket used to send audio frames.
     */
    val socket: VoiceUdpSocket

    /**
     *
     */
    val streams: Streams

    /**
     * An [AudioProvider] that will provide [AudioFrame]s when required.
     */
    val audioProvider: AudioProvider

    /**
     * The frame poller to use.
     */
    val framePoller: AudioFramePoller

    /**
     * A factory for [FrameInterceptor]s that's used whenever audio is ready to be sent. See [FrameInterceptor] and [DefaultFrameInterceptor].
     */
    val frameInterceptorFactory: (FrameInterceptorContext) -> FrameInterceptor

    /**
     * Connects to the gateway and begins the process of an audio-ready voice session.
     */
    fun connect()

    /**
     * Sets up this voice connection.
     *
     * @param configuration The voice gateway configuration.
     */
    suspend fun setup(configuration: VoiceGatewayConfiguration)

    /**
     * Disconnects from the voice server, does not change the voice state.
     */
    suspend fun disconnect()

    /**
     * Disconnects from the voice server AND leaves the voice channel.
     */
    suspend fun leave()

    /**
     * Releases all resources related to this VoiceConnection (except [gateway]) and then stops its CoroutineScope.
     */
    suspend fun shutdown()
}
