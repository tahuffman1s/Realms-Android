package com.realmsoffate.game.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Int deserializer that tolerates DeepSeek's common quirks:
 *   - `42` → 42 (normal path)
 *   - `"42"` → 42 (stringified number)
 *   - `""` / `"n/a"` / null → 0 (coerce to default)
 *   - booleans → 0/1 (extremely rare; defensive)
 *
 * Used on every numeric Spec field that's a required Int in our schema,
 * because DeepSeek in JSON mode occasionally emits empty strings where
 * integers belong (e.g. `"delta": ""` on an unused rep_delta entry).
 *
 * Symmetric: on encode, always writes a JSON number.
 */
object LenientInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor = Int.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = jd.decodeJsonElement()
        if (element !is JsonPrimitive) return 0
        element.intOrNull?.let { return it }
        if (element.isString) return element.content.trim().toIntOrNull() ?: 0
        element.booleanOrNull?.let { return if (it) 1 else 0 }
        return 0
    }
}

/**
 * Boolean deserializer that tolerates DeepSeek quirks:
 *   - `true`/`false` → normal
 *   - `"true"`/`"false"` → coerced
 *   - `1`/`0` → true/false
 *   - `""` or unknown → false
 */
object LenientBool : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = Boolean.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }

    override fun deserialize(decoder: Decoder): Boolean {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = jd.decodeJsonElement()
        if (element !is JsonPrimitive) return false
        element.booleanOrNull?.let { return it }
        if (element.isString) {
            return when (element.content.trim().lowercase()) {
                "true", "yes", "1", "pass" -> true
                else -> false
            }
        }
        element.intOrNull?.let { return it != 0 }
        return false
    }
}
