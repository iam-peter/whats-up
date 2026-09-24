package app.whatsup.config

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object AppConfigSerializer : Serializer<AppConfig> {
    override val defaultValue = AppConfig()

    override suspend fun readFrom(input: InputStream): AppConfig = try {
        json.decodeFromString(AppConfig.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read config", e)
    }

    override suspend fun writeTo(t: AppConfig, output: OutputStream) {
        output.write(json.encodeToString(AppConfig.serializer(), t).encodeToByteArray())
    }
}

/** Lives in files/datastore/, which Android Auto Backup includes (FR-C3). */
val Context.configStore: DataStore<AppConfig> by dataStore("whatsup_config.json", AppConfigSerializer)
