@file:OptIn(KordVoice::class)

package dev.kord.voice.handlers

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.EncryptionMode
import dev.kord.voice.FrameInterceptorConfiguration
import dev.kord.voice.VoiceConnection
import dev.kord.voice.encryption.strategies.LiteNonceStrategy
import dev.kord.voice.encryption.strategies.NormalNonceStrategy
import dev.kord.voice.encryption.strategies.SuffixNonceStrategy
import dev.kord.voice.gateway.*
import dev.kord.voice.udp.AudioFramePollerConfiguration
import io.ktor.util.network.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val udpLifeCycleLogger = KotlinLogging.logger { }

@OptIn(KordVoice::class)
internal class UdpLifeCycleHandler(
    flow: Flow<VoiceEvent>,
    private val connection: VoiceConnection
) : ConnectionEventHandler<VoiceEvent>(flow, "UdpInterceptor") {
    private var ssrc: UInt? by atomic(null)
    private var server: NetworkAddress? by atomic(null)

    private var audioSenderJob: Job? by atomic(null)

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun start() = coroutineScope {
        on<Ready> {
            ssrc = it.ssrc
            server = NetworkAddress(it.ip, it.port)

            val ip: NetworkAddress = connection.socket.discoverIp(server!!, ssrc!!.toInt())

            udpLifeCycleLogger.trace { "ip discovered for voice successfully" }

            val encryptionMode = when (connection.nonceStrategy) {
                is LiteNonceStrategy -> EncryptionMode.XSalsa20Poly1305Lite
                is NormalNonceStrategy -> EncryptionMode.XSalsa20Poly1305
                is SuffixNonceStrategy -> EncryptionMode.XSalsa20Poly1305Suffix
            }

            val selectProtocol = SelectProtocol(
                protocol = "udp",
                data = SelectProtocol.Data(
                    address = ip.hostname,
                    port = ip.port,
                    mode = encryptionMode
                )
            )

            connection.voiceGateway.send(selectProtocol)
        }

        on<SessionDescription> {
            with(connection) {
                val config = AudioFramePollerConfiguration(
                    ssrc = ssrc!!,
                    key = it.secretKey.toUByteArray().toByteArray(),
                    server = server!!,
                    interceptorConfiguration = FrameInterceptorConfiguration(gatewayBridge, voiceGateway, ssrc!!),
                    interceptor = connection.frameInterceptor,
                    nonceStrategy = connection.nonceStrategy,
                    provider = connection.audioProvider
                )

                audioSenderJob?.cancel()
                audioSenderJob = launch { framePoller.start(config) }
            }
        }

        on<Close> {
            audioSenderJob?.cancel()
            audioSenderJob = null
        }
    }
}
