import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.voice.AudioFrame
import mu.KotlinLogging
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val musicBotLogger = KotlinLogging.logger { }

@OptIn(KordVoice::class)
suspend fun main(args: Array<String>) {
    val kord = Kord(args.firstOrNull() ?: error("token required"))
    val players = DefaultAudioPlayerManager()
    AudioSourceManagers.registerRemoteSources(players)

    kord.on<ReadyEvent> {
        musicBotLogger.info { "Ready! Logged in as ${self.tag}" }
    }

    kord.on<MessageCreateEvent> {
        if (message.author?.isBot == true) return@on

        if (message.content == "!play") {
            val voiceChannel = message.getAuthorAsMember()
                ?.getVoiceStateOrNull()
                ?.getChannelOrNull()
                ?: return@on

            val player = players.createPlayer()
            val track = suspendCoroutine<AudioTrack?> { coro ->
                players.loadItemOrdered(
                    player, "ytsearch:leave me august ii", FunctionalResultHandler(
                        { coro.resume(it) },
                        { coro.resume(it.tracks.first()) },
                        { coro.resume(null) },
                        { coro.resume(null) },
                    )
                )
            } ?: return@on

            voiceChannel.connect {
                selfDeaf = true
                provide { AudioFrame.fromData(player.provide()?.data) }
            }

            player.playTrack(track)
        }
    }

    kord.login { presence { listening("your music!") } }
}
