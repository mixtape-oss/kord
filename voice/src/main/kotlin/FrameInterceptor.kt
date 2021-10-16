package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.gateway.SendSpeaking
import dev.kord.voice.gateway.VoiceGateway
import kotlin.properties.Delegates

/**
 * Variables that are accessible to any FrameInterceptor through the [VoiceConnection.frameInterceptorFactory].
 *
 * @param voiceGateway the underlying [VoiceGateway].
 * @param ssrc the current SSRC retrieved from Discord.
 */
@KordVoice
data class FrameInterceptorContext(
    val voiceGateway: VoiceGateway,
    val ssrc: UInt,
)

@KordVoice
class FrameInterceptorContextBuilder(var voiceGateway: VoiceGateway) {
    var ssrc: UInt by Delegates.notNull()

    fun build() = FrameInterceptorContext(voiceGateway, ssrc)
}

@KordVoice
internal inline fun FrameInterceptorContext(voiceGateway: VoiceGateway, builder: FrameInterceptorContextBuilder.() -> Unit) =
    FrameInterceptorContextBuilder(voiceGateway).apply(builder).build()

/**
 * An interceptor for audio frames before they are sent as packets.
 *
 * @see DefaultFrameInterceptor
 */
@KordVoice
fun interface FrameInterceptor {
    suspend fun intercept(frame: AudioFrame?): AudioFrame?
}

private const val FRAMES_OF_SILENCE_TO_PLAY = 5

/**
 * The default implementation for [FrameInterceptor].
 * Any custom implementation should extend this and call the super [intercept] method, or else
 * the speaking flags will not be sent!
 *
 * @param context the context for this interceptor.
 * @param speakingState the speaking state that will be used when there is audio data to be sent. By default, it is microphone-only.
 */
@KordVoice
open class DefaultFrameInterceptor(
    protected val context: FrameInterceptorContext,
    private val speakingState: SpeakingFlags = SpeakingFlags { +SpeakingFlag.Microphone }
) : FrameInterceptor {
    private val voiceGateway = context.voiceGateway

    private var framesOfSilence = 5
    private var isSpeaking = false

    private val nowSpeaking = SendSpeaking(speakingState, 0, context.ssrc)
    private val notSpeaking = SendSpeaking(SpeakingFlags(0), 0, context.ssrc)

    override suspend fun intercept(frame: AudioFrame?): AudioFrame? {
        if (frame != null || framesOfSilence > 0) { // is there something to process
            if (!isSpeaking && frame != null) { // if there is audio make sure we are speaking
                isSpeaking = true
                voiceGateway.send(nowSpeaking)
            }

            if (frame == null) { // if we don't have audio then make sure we know that we are sending a frame of silence
                if (--framesOfSilence == 0) { // we're done with frames of silence if we hit zero
                    isSpeaking = false
                    voiceGateway.send(notSpeaking)
                }
            }
            else if (framesOfSilence != FRAMES_OF_SILENCE_TO_PLAY) {
                framesOfSilence = FRAMES_OF_SILENCE_TO_PLAY // we're playing audio, lets reset the frames of silence.
            }

            return frame ?: AudioFrame.SILENCE
        }

        return frame
    }
}