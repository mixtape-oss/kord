package dev.kord.voice

import dev.kord.common.entity.DiscordVoicePacketData
import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.flow.Flow

// TODO: docs
/**
 * A gateway bridge, mainly used for handling voice related stuff.
 */
interface GatewayBridge {
    /**
     */
    val events: Flow<DiscordVoicePacketData>

    /**
     */
    suspend fun join(guildId: Snowflake, channelId: Snowflake, selfMute: Boolean, selfDeaf: Boolean)

    /**
     */
    suspend fun leave(guildId: Snowflake)
}

data class VoiceServerInfo(
    val endpoint: String?,
    val token: String,
    val sessionId: String,
    val guildId: Snowflake
)
