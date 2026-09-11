package com.airreload.shared.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadRecordCodecTest {
    @Test
    fun roundTripsEveryFieldWithoutDelimiterCollisions() {
        val original = DownloadRecord(
            id = "record|1",
            downloadedAt = 1_725_900_000_123,
            packageName = "com.example.app",
            label = "Demo | App ✓",
            versionName = "2.4 beta",
            versionCode = 42,
            sourceHost = "downloads.example.com",
            outcome = DownloadOutcomes.CANCELLED,
            artifactName = "0123abcd.apk",
            artifactBytes = 1_048_576,
        )

        assertEquals(original, DownloadRecordCodec.decode(DownloadRecordCodec.encode(original)))
    }

    @Test
    fun readsRecordsCreatedByTheOriginalAndroidCodec() {
        val existing = "1|cmVjb3JkLTE|1725900000123|Y29tLmV4YW1wbGUuYXBw|RGVtbyBBcHA|Mi40|42|ZG93bmxvYWRzLmV4YW1wbGUuY29t|aW5zdGFsbGVk|MDEyM2FiY2QuYXBr|1048576"

        val decoded = DownloadRecordCodec.decode(existing)

        assertEquals("record-1", decoded?.id)
        assertEquals("Demo App", decoded?.label)
        assertEquals(DownloadOutcomes.INSTALLED, decoded?.outcome)
    }

    @Test
    fun rejectsMalformedRecords() {
        assertNull(DownloadRecordCodec.decode(null))
        assertNull(DownloadRecordCodec.decode(""))
        assertNull(DownloadRecordCodec.decode("1|bad"))
        assertNull(DownloadRecordCodec.decode("1|%%%|not-a-date||||||||"))
    }
}
