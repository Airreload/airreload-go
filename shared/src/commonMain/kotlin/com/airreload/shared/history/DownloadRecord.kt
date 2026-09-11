package com.airreload.shared.history

data class DownloadRecord(
    val id: String,
    val downloadedAt: Long,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val sourceHost: String,
    val outcome: String,
    val artifactName: String,
    val artifactBytes: Long,
) {
    fun withOutcome(value: String): DownloadRecord = copy(outcome = value.trim())
}
