package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Backs up all settings and resources to Google Drive as backup_{YYMMDD-HHmm}.json.
 * Location: My Drive / FastMediaSorter / backup_{YYMMDD-HHmm}.json
 */
class BackupToGoogleDriveUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val googleDriveClient: GoogleDriveRestClient,
    private val buildBackupPayloadUseCase: BuildBackupPayloadUseCase
) {
    companion object {
        const val FOLDER_NAME = "FastMediaSorter"
        private const val FILE_NAME_PREFIX = "backup_"
        private const val FILE_NAME_SUFFIX = ".json"
        private const val MIME_JSON = "application/json"

        fun generateFileName(): String {
            val sdf = SimpleDateFormat("yyMMdd-HHmm", Locale.US)
            return "$FILE_NAME_PREFIX${sdf.format(Date())}$FILE_NAME_SUFFIX"
        }

        private val README_EN = """
            |# FastMediaSorter - Backup Folder
            |
            |This folder is created by the **FastMediaSorter** Android app.
            |
            |## About the App
            |
            |FastMediaSorter is a media browser and organizer for Android that supports
            |local storage, SMB/SFTP/FTP network shares, and cloud providers
            |(Google Drive, OneDrive, Dropbox). It lets you browse, sort, view, and
            |manage media files across all your sources from a single app.
            |
            |## What's Inside
            |
            |The `backup_YYMMDD-HHmm.json` files contain app configuration backups:
            |
            |- **Settings** - all app preferences (UI, media filters, playback, network,
            |  translation/OCR, cache, etc.)
            |- **Resource list** - configured media sources (local folders, network shares,
            |  cloud folders) with their display and behavior settings
            |- **Favorites** - bookmarked media files with metadata
            |
            |Backups do **not** contain:
            |
            |- Media files, thumbnails, or cached data
            |- Passwords, OAuth tokens, or other credentials
            |- Database indexes
            |
            |## Restoring
            |
            |To restore, open FastMediaSorter → Settings → Backup → "Restore from Google Drive".
            |The app will pick the most recent backup file automatically.
            |
            |After restoring, you may need to re-authenticate network and cloud resources.
        """.trimMargin()

        private val README_RU = """
            |# FastMediaSorter - Папка резервных копий
            |
            |Эта папка создана приложением **FastMediaSorter** для Android.
            |
            |## О приложении
            |
            |FastMediaSorter - браузер и органайзер медиафайлов для Android с поддержкой
            |локального хранилища, сетевых ресурсов SMB/SFTP/FTP и облачных провайдеров
            |(Google Drive, OneDrive, Dropbox). Позволяет просматривать, сортировать
            |и управлять файлами из всех источников в одном приложении.
            |
            |## Что хранится
            |
            |Файлы `backup_YYMMDD-HHmm.json` содержат резервные копии конфигурации:
            |
            |- **Настройки** - все параметры приложения (интерфейс, фильтры медиа,
            |  воспроизведение, сеть, перевод/OCR, кэш и т.д.)
            |- **Список ресурсов** - настроенные источники медиа (локальные папки,
            |  сетевые шары, облачные папки) с параметрами отображения и поведения
            |- **Избранное** - закладки медиафайлов с метаданными
            |
            |Резервные копии **не** содержат:
            |
            |- Медиафайлы, миниатюры или кэшированные данные
            |- Пароли, OAuth-токены и другие учётные данные
            |- Индексы базы данных
            |
            |## Восстановление
            |
            |Для восстановления откройте FastMediaSorter → Настройки → Резервное копирование →
            |«Восстановить из Google Drive». Приложение автоматически выберет последнюю копию.
            |
            |После восстановления может потребоваться повторная аутентификация
            |сетевых и облачных ресурсов.
        """.trimMargin()

        private val README_UK = """
            |# FastMediaSorter - Папка резервних копій
            |
            |Цю папку створено додатком **FastMediaSorter** для Android.
            |
            |## Про додаток
            |
            |FastMediaSorter - браузер та органайзер медіафайлів для Android з підтримкою
            |локального сховища, мережевих ресурсів SMB/SFTP/FTP та хмарних провайдерів
            |(Google Drive, OneDrive, Dropbox). Дозволяє переглядати, сортувати
            |та керувати файлами з усіх джерел в одному додатку.
            |
            |## Що зберігається
            |
            |Файли `backup_YYMMDD-HHmm.json` містять резервні копії конфігурації:
            |
            |- **Налаштування** - всі параметри додатка (інтерфейс, фільтри медіа,
            |  відтворення, мережа, переклад/OCR, кеш тощо)
            |- **Список ресурсів** - налаштовані джерела медіа (локальні папки,
            |  мережеві ресурси, хмарні папки) з параметрами відображення та поведінки
            |- **Обране** - закладки медіафайлів з метаданими
            |
            |Резервні копії **не** містять:
            |
            |- Медіафайли, мініатюри або кешовані дані
            |- Паролі, OAuth-токени та інші облікові дані
            |- Індекси бази даних
            |
            |## Відновлення
            |
            |Для відновлення відкрийте FastMediaSorter → Налаштування → Резервне копіювання →
            |«Відновити з Google Drive». Додаток автоматично обере останню копію.
            |
            |Після відновлення може знадобитися повторна автентифікація
            |мережевих та хмарних ресурсів.
        """.trimMargin()
    }

    data class BackupResult(
        val resourceCount: Int,
        val favoritesCount: Int,
        val accountEmail: String
    )

    suspend operator fun invoke(): Result<BackupResult> = withContext(Dispatchers.IO) {
        try {
            if (!googleDriveClient.isAuthenticated()) {
                return@withContext Result.failure(Exception("Not authenticated with Google Drive"))
            }

            val accountEmail = googleDriveClient.getAccountEmail() ?: "Unknown"

            // 1. Build unified payload (shared with the local-file export path).
            Timber.d("S0406: backup unified payload to Google Drive")
            val payload = buildBackupPayloadUseCase()
            val resourceCount = payload.resources?.size ?: 0
            val favoritesCount = payload.favorites?.size ?: 0

            // 2. Serialize to JSON
            val gson = GsonBuilder().setPrettyPrinting().create()
            val json = gson.toJson(payload)
            val jsonBytes = json.toByteArray(Charsets.UTF_8)

            // 4. Find or create app folder
            val folderId = findOrCreateFolder()
                ?: return@withContext Result.failure(Exception("Failed to create backup folder on Google Drive"))

            // 5. Upload new timestamped backup
            val fileName = generateFileName()
            val uploadResult = googleDriveClient.uploadFile(
                inputStream = ByteArrayInputStream(jsonBytes),
                fileName = fileName,
                mimeType = MIME_JSON,
                parentFolderId = folderId,
                progressCallback = null
            )

            when (uploadResult) {
                is CloudResult.Success -> {
                    Timber.i("Backup uploaded as $fileName: $resourceCount resources, $favoritesCount favorites")
                    Result.success(BackupResult(resourceCount, favoritesCount, accountEmail))
                }
                is CloudResult.Error -> {
                    Timber.e("Backup upload failed: ${uploadResult.message}")
                    Result.failure(Exception(uploadResult.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Backup to Google Drive failed")
            Result.failure(e)
        }
    }

    private suspend fun findOrCreateFolder(): String? {
        // Find existing folder by exact name
        val findResult = googleDriveClient.findFolderByName(FOLDER_NAME)
        if (findResult is CloudResult.Success && findResult.data != null) {
            return findResult.data.id
        }

        val createResult = googleDriveClient.createFolder(FOLDER_NAME, null)
        return when (createResult) {
            is CloudResult.Success -> {
                val folderId = createResult.data.id
                uploadReadme(folderId)
                folderId
            }
            is CloudResult.Error -> {
                Timber.e("Failed to create folder: ${createResult.message}")
                null
            }
        }
    }

    private suspend fun uploadReadme(folderId: String) {
        try {
            val language = settingsRepository.getSettings().first().language
            val content = getReadmeContent(language)
            val bytes = content.toByteArray(Charsets.UTF_8)
            googleDriveClient.uploadFile(
                inputStream = ByteArrayInputStream(bytes),
                fileName = "README.md",
                mimeType = "text/markdown",
                parentFolderId = folderId,
                progressCallback = null
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to upload README.md (non-critical)")
        }
    }

    private fun getReadmeContent(language: String): String = when (language) {
        "ru" -> README_RU
        "uk" -> README_UK
        else -> README_EN
    }
}
