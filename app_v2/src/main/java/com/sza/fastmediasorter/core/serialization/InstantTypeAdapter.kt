package com.sza.fastmediasorter.core.serialization

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.Instant

/**
 * Gson wire adapter for [Instant], registered on the injected application Gson.
 *
 * S1668: with no adapter registered, Gson serializes a platform [Instant] by reflecting over that class's own
 * private `seconds`/`nanos` fields. Reading those fields is not a guaranteed capability - the JVM refuses it
 * outright under JPMS (S1657 had to open `java.base/java.time` to the test worker just to let the serialization
 * tests run), and ART applies its non-SDK interface filter to the same family of fields. A refusal there is
 * silent: the value is written as `{}` and read back as epoch 0, so a stored account binding loses its timestamp
 * without any failure surfacing.
 *
 * The emitted shape is deliberately byte-identical to what the reflective path produced, `seconds` then `nanos`,
 * because blobs written by earlier builds already sit in the `primary_google_account_v1` encrypted preferences
 * and must keep parsing across an app update.
 */
class InstantTypeAdapter : TypeAdapter<Instant>() {

    override fun write(out: JsonWriter, value: Instant?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name(FIELD_SECONDS).value(value.epochSecond)
        out.name(FIELD_NANOS).value(value.nano)
        out.endObject()
    }

    /**
     * A missing field counts as 0, so `{}` decodes to [Instant.EPOCH] instead of throwing: that is exactly the
     * blob a device wrote while the reflective path was blocked, and it already means epoch. Turning a read of it
     * into a crash would replace a wrong timestamp with a lost binding.
     */
    override fun read(input: JsonReader): Instant? {
        if (input.peek() == JsonToken.NULL) {
            input.nextNull()
            return null
        }
        var seconds = 0L
        var nanos = 0
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                FIELD_SECONDS -> seconds = input.nextLong()
                FIELD_NANOS -> nanos = input.nextInt()
                else -> input.skipValue()
            }
        }
        input.endObject()
        return Instant.ofEpochSecond(seconds, nanos.toLong())
    }

    private companion object {
        const val FIELD_SECONDS = "seconds"
        const val FIELD_NANOS = "nanos"
    }
}
