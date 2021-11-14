@file:Suppress("EXPERIMENTAL_API_USAGE")

package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.DiscordVoiceServerUpdateData
import dev.kord.common.entity.DiscordVoiceState
import dev.kord.common.entity.Snowflake
import dev.kord.voice.encryption.strategies.LiteNonceStrategy
import dev.kord.voice.encryption.strategies.NonceStrategy
import dev.kord.voice.exception.VoiceConnectionInitializationException
import dev.kord.voice.gateway.DefaultVoiceGatewayBuilder
import dev.kord.voice.gateway.VoiceGateway
import dev.kord.voice.gateway.VoiceGatewayConfiguration
import dev.kord.voice.streams.DefaultStreams
import dev.kord.voice.streams.NOPStreams
import dev.kord.voice.streams.Streams
import dev.kord.voice.streams.StreamsFactory
import dev.kord.voice.udp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

@KordVoice
class DefaultVoiceConnectionBuilder(
    var gateway: GatewayBridge,
    var selfId: Snowflake,
    var channelId: Snowflake,
    var guildId: Snowflake
) {
    /**
     * The amount in milliseconds to wait for the events required to create a [DefaultVoiceConnection]. Default is 5000, or 5 seconds.
     */
    var timeout: Long = 5000

    /**
     * The nonce strategy to be used for the encryption of audio packets.
     * If `null`, [dev.kord.voice.encryption.strategies.LiteNonceStrategy] will be used.
    */
    var nonceStrategy: NonceStrategy? = null

    /**
     * The [AudioProvider] for this [DefaultVoiceConnection]. No audio will be provided when one is not set.
     */
    var audioProvider: AudioProvider? = null

    fun provide(provider: AudioProvider) {
        this.audioProvider = provider
    }

    /**
     * The [AudioFramePoller] factory for this [DefaultVoiceConnection].
     * When one is not set, a factory will be used to create the default frame poller, see [DefaultAudioFramePoller].
     * This factory will be used to create a new [AudioFramePoller] whenever the connection is established.
     */
    var framePollerFactory: AudioFramePollerFactory = DefaultAudioFramePollerFactory

    fun framePoller(factory: AudioFramePollerFactory) {
        this.framePollerFactory = factory
    }

    /**
     * The [FrameInterceptor] factory for this [DefaultVoiceConnection].
     * When one is not set, a factory will be used to create the default interceptor, see [DefaultFrameInterceptor].
     * This factory will be used to create a new [FrameInterceptor] whenever audio is ready to be sent.
     */
    var frameInterceptor: FrameInterceptor? = null

    fun frameInterceptor(interceptor: Flow<AudioFrame?>.(FrameInterceptorConfiguration) -> Flow<AudioFrame?>) {
        frameInterceptor = FrameInterceptor(interceptor)
    }

    /**
     * A boolean indicating whether your voice state will be muted.
     */
    var selfMute: Boolean = false

    /**
     * A boolean indicating whether your voice state will be deafened.
     */
    var selfDeaf: Boolean = false

    private var voiceGatewayBuilder: (DefaultVoiceGatewayBuilder.() -> Unit)? = null

    /**
     * A [dev.kord.voice.udp.VoiceUdpSocket] implementation to be used. If null, a default will be used.
     */
    var udpSocket: VoiceUdpSocket? = null

    /**
     * A flag to control the implementation of [streams]. Set to false by default.
     * When set to false, a NOP implementation will be used.
     * When set to true, a proper receiving implementation will be used.
     */
    var receiveVoice: Boolean = false

    /**
     * A [Streams] implementation to be used. This will override the [receiveVoice] flag.
     */
    var streams: Streams? = null

    /**
     * A builder to customize the voice connection's underlying [VoiceGateway].
     */
    fun voiceGateway(builder: DefaultVoiceGatewayBuilder.() -> Unit) {
        this.voiceGatewayBuilder = builder
    }

    private suspend fun updateVoiceState(): Pair<VoiceConnectionData, VoiceGatewayConfiguration> = coroutineScope {
        val voiceStateDeferred = async {
            withTimeoutOrNull(timeout) {
                gateway.events.filterIsInstance<DiscordVoiceState>()
                    .filter { it.guildId.value == guildId && it.userId == selfId }
                    .first()
            }
        }

        val voiceServerDeferred = async {
            withTimeoutOrNull(timeout) {
                gateway.events.filterIsInstance<DiscordVoiceServerUpdateData>()
                    .filter { it.guildId == guildId }
                    .first()
            }
        }

        gateway.join(
            guildId = guildId,
            channelId = channelId,
            selfMute = selfMute,
            selfDeaf = selfDeaf,
        )

        val voiceServer = voiceServerDeferred.await()
        val voiceState = voiceStateDeferred.await()

        if (voiceServer == null || voiceState == null)
            throw VoiceConnectionInitializationException("Did not receive a VoiceStateUpdate and or a VoiceServerUpdate in time!")

        val data = VoiceConnectionData(selfId, guildId, voiceState.sessionId)
        val config = VoiceGatewayConfiguration(voiceServer.token, "wss://${voiceServer.endpoint}?v=4")

        data to config
    }

    /**
     * @throws dev.kord.voice.exception.VoiceConnectionInitializationException when there was a problem retrieving voice information from Discord.
     */
    suspend fun build(): DefaultVoiceConnection {
        val (voiceConnectionData, initialGatewayConfiguration) = updateVoiceState()
        val connection = DefaultVoiceConnection(
            data = voiceConnectionData,
            audioProvider = audioProvider ?: EmptyAudioPlayerProvider,
            frameInterceptor = frameInterceptor ?: DefaultFrameInterceptor(),
            gatewayBridge = gateway,
            streamsFactory = { streams ?: if (receiveVoice) DefaultStreams(it) else NOPStreams },
            udpSocket = udpSocket,
            voiceGatewayBuilder = voiceGatewayBuilder ?: {},
            framePollerFactory = framePollerFactory,
            nonceStrategy = nonceStrategy ?: LiteNonceStrategy()
        )

        connection.voiceGatewayConfiguration = initialGatewayConfiguration
        return connection
    }

    // we can't use the SAM feature or else we break the IR backend, so lets just use this object instead
    private object EmptyAudioPlayerProvider : AudioProvider {
        override suspend fun provide(): AudioFrame? = null
    }
}
