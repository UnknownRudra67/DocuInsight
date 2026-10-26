package com.example.data.repository

import android.util.Log
import com.example.data.model.FirestoreInsight
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiRepository"

class AiRepository {

    // Lazy initialization of standard generative model for summarization and Q&A
    private val standardModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    // Structured JSON schema for key insight extraction with source citations
    private val insightsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "insights" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Concise title of the key finding"),
                            "content" to Schema.string(description = "Detailed insight or fact extracted from text"),
                            "sourceReference" to Schema.string(description = "Exact source location or quote reference (e.g. 'Page 3, Paragraph 2: Revenue surged by...')"),
                            "category" to Schema.enumeration(
                                values = listOf("key_takeaway", "risk", "action_item", "financial", "statistic"),
                                description = "Category classification of this insight"
                            )
                        ),
                        optionalProperties = emptyList()
                    ),
                    description = "List of key insights extracted from the document"
                )
            ),
            optionalProperties = emptyList()
        )
    }

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = insightsSchema
            }
        )
    }

    /**
     * Generates an executive summary for a document using streaming response.
     */
    fun generateSummaryStream(title: String, documentContent: String): Flow<String> = flow {
        val prompt = """
            You are an expert executive document analyst.
            Please provide a comprehensive, beautifully structured executive summary of the following document.
            Document Title: "$title"
            
            Document Content:
            $documentContent
            
            Structure the summary with:
            - 🎯 Executive Overview
            - 🔑 Core Highlights
            - 📊 Critical Metrics & Findings
            - 💡 Strategic Recommendations
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            standardModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                accumulated.append(text)
                emit(accumulated.toString())
            }
        }
    }

    /**
     * Extracts structured key insights with strict source references.
     */
    suspend fun extractKeyInsights(
        documentId: String,
        ownerId: String,
        collaborators: List<String>,
        documentContent: String
    ): List<FirestoreInsight> {
        val prompt = """
            Analyze the following document and extract the top 4-8 crucial key insights.
            For EVERY insight, you MUST provide an explicit sourceReference pointing to the exact section, page, or quoted sentence in the text so users can verify it.
            
            Document Content:
            $documentContent
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Model returned empty insight text.")
            val root = JSONObject(jsonText)
            val insightsArray = root.optJSONArray("insights") ?: return emptyList()

            val result = mutableListOf<FirestoreInsight>()
            for (i in 0 until insightsArray.length()) {
                val item = insightsArray.getJSONObject(i)
                result.add(
                    FirestoreInsight(
                        id = "ins_${System.currentTimeMillis()}_$i",
                        documentId = documentId,
                        ownerId = ownerId,
                        collaborators = collaborators,
                        title = item.optString("title", "Insight #${i + 1}"),
                        content = item.optString("content", ""),
                        sourceReference = item.optString("sourceReference", "Extracted from source content"),
                        category = item.optString("category", "key_takeaway")
                    )
                )
            }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract structured insights", e)
            throw e
        }
    }

    /**
     * Answers interactive follow-up questions regarding the document.
     */
    fun askFollowUpQuestionStream(
        documentTitle: String,
        documentContent: String,
        question: String,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        val contextPrompt = """
            You are a helpful AI research assistant analyzing the document: "$documentTitle".
            Answer the user's question accurately based STRICTLY on the document context below.
            Always include relevant source citations or page/section quotes where applicable.
            
            Document Content:
            $documentContent
            
            User Question:
            $question
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            standardModel.generateContentStream(contextPrompt).collect { chunk ->
                accumulated.append(chunk.text.orEmpty())
                emit(accumulated.toString())
            }
        }
    }
}
