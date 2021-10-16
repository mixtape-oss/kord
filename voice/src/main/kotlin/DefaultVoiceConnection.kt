package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.voice.gateway.DefaultVoiceGatewayBuilder
import dev.kord.voice.gateway.VoiceGateway
import dev.kord.voice.gateway.VoiceGatewayConfiguration
import dev.kord.voice.handlers.StreamsHandler
import dev.kord.voice.handlers.UdpLifeCycleHandler
import dev.kord.voice.handlers.VoiceUpdateEventHandler
import dev.kord.voice.streams.Streams
import dev.kord.voice.streams.StreamsFactory
import dev.kord.voice.udp.*
import dev.kord.voice.udp.AudioFramePoller
import io.ktor.util.network.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private val voiceConnectionLogger = KotlinLogging.logger { }

@KordVoice
data class VoiceContext(
    val udpSocket: VoiceUdpSocket,
    val voiceGateway: VoiceGateway,
    val voiceConnection: VoiceConnection,
)

/**
 * A [VoiceConnection] is an adapter that forms the concept of a voice connection, or a connection between you and Discord voice servers.
 *
 * @param gatewayBridge the [GatewayBridge] that handles events for the guild this [VoiceConnection] represents.
 * @param data the data representing this [VoiceConnection].
 * @param audioProvider a [AudioProvider] that will provide [AudioFrame] when required.
 * @param frameInterceptorFactory a factory for [FrameInterceptor]s that is used whenever audio is ready to be sent. See [FrameInterceptor] and [DefaultFrameInterceptor].
 * @param framePollerFactory A factory for [AudioFramePoller]s that's used to send frames to the voice server. See [AudioFramePoller] and [dev.kord.voice.udp.DefaultAudioFramePoller].
 */
@KordVoice
class DefaultVoiceConnection(
    override val data: VoiceConnectionData,
    override val audioProvider: AudioProvider,
    override val frameInterceptorFactory: (FrameInterceptorContext) -> FrameInterceptor,
    private val gatewayBridge: GatewayBridge,
    streamsFactory: StreamsFactory,
    udpSocket: VoiceUdpSocket?,
    voiceGatewayBuilder: DefaultVoiceGatewayBuilder.() -> Unit = {},
    private val framePollerFactory: AudioFramePollerFactory
) : VoiceConnection {
    val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + CoroutineName("kord-voice-connection[${data.guildId.value}"))

    override val socket: VoiceUdpSocket = udpSocket ?: GlobalVoiceUdpSocket
    override val voiceGateway: VoiceGateway
    override val framePoller: AudioFramePoller
    override val streams: Streams

    override var voiceServer: NetworkAddress? by atomic(null)
        internal set

    internal var voiceGatewayConfiguration: VoiceGatewayConfiguration? = null

    val context: VoiceContext
        get() = VoiceContext(socket, voiceGateway, this)


    init {
        voiceGateway = DefaultVoiceGatewayBuilder(data.selfId, data.guildId, data.sessionId)
            .apply(voiceGatewayBuilder)
            .build()
        framePoller = framePollerFactory.create(context)
        streams = streamsFactory.create(context)

        with(scope) {
            launch { VoiceUpdateEventHandler(gatewayBridge.events, this@DefaultVoiceConnection).start() }
            launch { StreamsHandler(voiceGateway.events, streams).start() }
            launch { UdpLifeCycleHandler(voiceGateway.events, this@DefaultVoiceConnection).start() }
        }
    }

    override fun connect() {
        val configuration = voiceGatewayConfiguration
        require (configuration != null) {
            "This VoiceConnection hasn't been set up yet."
        }

        scope.launch {
            voiceGateway.start(configuration)
        }
    }

    override suspend fun setup(configuration: VoiceGatewayConfiguration) {
        voiceGatewayConfiguration = configuration
    }

    override suspend fun disconnect() {
        voiceGateway.stop()
        socket.stop()
    }

    override suspend fun leave() {
        disconnect()

        gatewayBridge.leave(guildId = data.guildId,)
    }

    override suspend fun shutdown() {
        leave()
        voiceGateway.detach()

        scope.cancel()
    }
}

/**
 * Builds a [DefaultVoiceConnection] configured by the [builder].
 *
 * @param gateway the [GatewayBridge].
 * @param selfId the id of yourself.
 * @param channelId the id of the initial voice channel you are connecting to.
 * @param guildId the id of the guild the voice channel resides in.
 * @param builder the builder.
 *
 * @return a [DefaultVoiceConnection] that is ready to be used.
 *
 * @throws dev.kord.voice.exception.VoiceConnectionInitializationException when there was a problem retrieving voice information from Discord.
 */
@KordVoice
@OptIn(ExperimentalContracts::class)
suspend inline fun VoiceConnection(
    gateway: GatewayBridge,
    selfId: Snowflake,
    channelId: Snowflake,
    guildId: Snowflake,
    builder: DefaultVoiceConnectionBuilder.() -> Unit = {}
): DefaultVoiceConnection {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return DefaultVoiceConnectionBuilder(gateway, selfId, channelId, guildId).apply(builder).build()
}
