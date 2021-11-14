package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.gateway.VoiceGateway
import kotlinx.coroutines.flow.Flow

@KordVoice
public data class FrameInterceptorConfiguration(
    val gatewayBridge: GatewayBridge,
    val voiceGateway: VoiceGateway,
    val ssrc: UInt
)

/**
 * An interceptor for audio frames before they are sent as packets.
 *
 * @see DefaultFrameInterceptor
 */
@KordVoice
public fun interface FrameInterceptor {
    public fun Flow<AudioFrame?>.intercept(configuration: FrameInterceptorConfiguration): Flow<AudioFrame?>
}
