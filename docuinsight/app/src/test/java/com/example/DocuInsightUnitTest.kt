package com.example

import com.example.data.model.FirestoreAuditLog
import com.example.data.model.FirestoreDocument
import com.example.data.model.FirestoreInsight
import com.example.data.repository.AuditRepository
import com.example.util.LocalEncryptionHelper
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocuInsightUnitTest {

    @Test
    fun testLocalEncryptionDecryptionRoundtrip() {
        val originalText = "Confidential Financial Executive Briefing Q3 2026: Revenue $842M"
        val encrypted = LocalEncryptionHelper.encrypt(originalText)

        assertNotEquals(originalText, encrypted)
        assertTrue(encrypted.isNotBlank())

        val decrypted = LocalEncryptionHelper.decrypt(encrypted)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testEmptyStringEncryption() {
        val empty = ""
        val encrypted = LocalEncryptionHelper.encrypt(empty)
        assertEquals("", encrypted)

        val decrypted = LocalEncryptionHelper.decrypt(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun testFirestoreDocumentDefaults() {
        val doc = FirestoreDocument()
        assertEquals("", doc.id)
        assertEquals("", doc.ownerId)
        assertEquals("", doc.title)
        assertEquals("txt", doc.format)
        assertEquals("pending", doc.summaryStatus)
        assertTrue(doc.collaborators.isEmpty())
    }

    @Test
    fun testFirestoreInsightDefaults() {
        val insight = FirestoreInsight(
            title = "Revenue Growth",
            sourceReference = "Page 4, Paragraph 2"
        )
        assertEquals("Revenue Growth", insight.title)
        assertEquals("Page 4, Paragraph 2", insight.sourceReference)
        assertEquals("key_takeaway", insight.category)
    }
}
