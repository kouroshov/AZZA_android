package com.azza.core.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * apps/api serialises a Postgres BIGINT as a JSON *string* (its BigInt
 * polyfill, to stay precision-safe) and a NUMERIC as a string too (Prisma's
 * Decimal). Some hand-built DTOs send plain numbers instead. These accept both.
 */
object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long {
        val primitive = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        return primitive.content.toLong()
    }
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(value.toString())
}

object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double {
        val primitive: JsonPrimitive = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        return primitive.content.toDouble()
    }
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}
