package com.mytaxi.apis.phraseapi.task

import com.mytaxi.apis.phraseapi.client.PhraseApiClient
import com.mytaxi.apis.phraseapi.client.PhraseApiClientImpl
import com.mytaxi.apis.phraseapi.client.model.PhraseLocale
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("TooGenericExceptionCaught")
class PhraseAppSyncTask(
    private val config: PhraseAppSyncTaskConfig
) : Runnable {

    private var log = LoggerFactory.getLogger(PhraseAppSyncTask::class.java.name)

    private var messagesDirectory: Path

    private val client: PhraseApiClient

    init {
        client = PhraseApiClientImpl(config.url, config.authKey)

        try {
            val classPathResource = ClassPathResource("/").file.path
            messagesDirectory = Paths.get("$classPathResource/${config.messagesFolder}")
        } catch (e: Exception) {
            messagesDirectory = Paths.get(config.generatedResourcesFolder + config.messagesFolder)
            log.error("could not get default ClassPathResource. use /generated-resources/ instead")
        }

        Files.createDirectories(messagesDirectory)
    }

    override fun run() {
        try {
            log.info("Phrase App sync started")
            client.locales(config.projectId)
                .orEmpty()
                .forEach {
                    updateLocaleFile(it)
                }
            log.info("Phrase App sync finished")
        } catch (ex: Exception) {
            log.warn("PhraseApp sync failed", ex)
        }
    }

    private fun updateLocaleFile(locale: PhraseLocale) {
        try {
            val byteArray = client.downloadLocaleAsProperties(config.projectId, locale.id, false)
            if (byteArray != null) {
                val fileName = createFileName(locale.code)
                val path = messagesDirectory.resolve(fileName)
                if (!Files.exists(path)) {
                    Files.createFile(path)
                }
                Files.write(path, byteArray)
            }
        } catch (ex: Exception) {
            log.warn("PhraseApp sync failed for $locale", ex)
        }
    }

    private fun createFileName(code: String): String {
        return config.messagesFilePrefix + code.replace("-", "_") + config.messagesFilePostfix
    }
}

data class PhraseAppSyncTaskConfig(
    val url: String,
    val authKey: String,
    val projectId: String,
    val generatedResourcesFolder: String = "generated-resources/",
    val messagesFolder: String = "messages/",
    val messagesFilePostfix: String = ".properties",
    val messagesFilePrefix: String = "messages_"
)
