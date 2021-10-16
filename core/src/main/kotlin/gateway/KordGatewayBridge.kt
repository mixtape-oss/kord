package dev.kord.core.gateway

import dev.kord.common.entity.DiscordVoicePacketData
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.exception.GatewayNotFoundException
import dev.kord.gateway.Gateway
import dev.kord.gateway.UpdateVoiceStatus
import dev.kord.gateway.VoiceUpdatePacket
import dev.kord.voice.GatewayBridge
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.*

internal class KordGatewayBridge(private val kord: Kord) : GatewayBridge {
    @Suppress("NOTHING_TO_INLINE")
    companion object {
        inline fun gatewayNotFound(guildId: Snowflake): Nothing {
            throw GatewayNotFoundException("Wasn't able to find a gateway for the guild: $guildId!")
        }
    }

    override val events: Flow<DiscordVoicePacketData> = kord.gateway.events
        .buffer(UNLIMITED)
        .map { it.event }
        .filterIsInstance<VoiceUpdatePacket>()
        .map { it.data }

    override suspend fun join(guildId: Snowflake, channelId: Snowflake, selfMute: Boolean, selfDeaf: Boolean) {
        val gateway = kord.findGatewayForGuild(guildId)
            ?: gatewayNotFound(guildId)

        gateway.send(UpdateVoiceStatus(guildId, channelId, selfMute, selfDeaf))
    }

    override suspend fun leave(guildId: Snowflake) {
        val gateway = kord.findGatewayForGuild(guildId)
            ?: gatewayNotFound(guildId)

        gateway.send(UpdateVoiceStatus(guildId, null, selfMute = false, selfDeaf = false))
    }
}

internal fun Kord.findGatewayForGuild(guildId: Snowflake): Gateway? {
    val shard = guildId.value.shr(22).toLong() % resources.shards.totalShards.coerceAtLeast(1)
    return gateway.gateways[shard.toInt()]
}
