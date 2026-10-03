package `in`.nrkmart.cricscore

import android.net.Uri
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object WebShareApi {
    private const val DEFAULT_BASE_URL = "https://cricleague.nrkmart.in"
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 15000

    private val gson = Gson()

    data class ShareLinkPayload(
        val matchId: String,
        val spectatorToken: String,
        val baseUrl: String
    )

    fun parseShareUrl(rawUrl: String): ShareLinkPayload? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null

        val extractedUrl = Regex("https?://\\S+")
            .find(trimmed)
            ?.value
            ?.trimEnd('.', ',', ';')
            ?: trimmed

        val uri = Uri.parse(extractedUrl)
        val matchId = uri.getQueryParameter("matchId")?.trim().orEmpty()
        val spectatorToken = uri.getQueryParameter("st")?.trim().orEmpty()

        if (matchId.isEmpty() || spectatorToken.isEmpty()) return null

        val baseUrl = CloudSyncManager.apiBaseUrl()

        return ShareLinkPayload(
            matchId = matchId,
            spectatorToken = spectatorToken,
            baseUrl = baseUrl
        )
    }

    fun fetchSharedMatch(payload: ShareLinkPayload): Match {
        val normalizedBase = payload.baseUrl.trimEnd('/')
        val encodedToken = Uri.encode(payload.spectatorToken)
        val url = URL("$normalizedBase/matches/${payload.matchId}?st=$encodedToken")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use {
                BufferedReader(InputStreamReader(it)).readText()
            }.orEmpty()

            if (status !in 200..299) {
                throw IllegalStateException("Unable to fetch live share (HTTP $status)")
            }

            gson.fromJson(body, Match::class.java)?.safeCopy()
                ?: throw IllegalStateException("Invalid live share payload")
        } finally {
            connection.disconnect()
        }
    }

    fun appBaseUrl(): String = DEFAULT_BASE_URL
}
