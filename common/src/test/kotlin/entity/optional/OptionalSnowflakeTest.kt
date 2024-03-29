package dev.kord.common.entity.optional

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class OptionalSnowflakeTest {


    @Serializable
    class EmptyOptionalEntity(val value: OptionalSnowflake = OptionalSnowflake.Missing)

    @Test
    fun `deserializing nothing in optional assigns Missing`(){
        @Language("json")
        val json = """{}"""


        val entity = Json.decodeFromString<EmptyOptionalEntity>(json)

        assert(entity.value is OptionalSnowflake.Missing)
    }


    @Serializable
    class NullOptionalEntity(@Suppress("unused") val value: OptionalSnowflake = OptionalSnowflake.Missing)

    @Test
    fun `deserializing null in optional throws SerializationException`(){
        @Language("json")
        val json = """{ "value":null }"""

        org.junit.jupiter.api.assertThrows<SerializationException> {
            Json.decodeFromString<NullOptionalEntity>(json)
        }
    }


    @Serializable
    class ValueOptionalEntity(@Suppress("unused") val value: OptionalSnowflake = OptionalSnowflake.Missing)

    @Test
    fun `deserializing value in optional assigns Value`(){
        @Language("json")
        val json = """{ "value":5 }"""

        val entity = Json.decodeFromString<ValueOptionalEntity>(json)
        require(entity.value is OptionalSnowflake.Value)

        Assertions.assertEquals(Snowflake(5u), entity.value.value)
    }

}
