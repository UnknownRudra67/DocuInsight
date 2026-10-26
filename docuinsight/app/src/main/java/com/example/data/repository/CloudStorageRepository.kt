package com.example.data.repository

data class CloudFileItem(
    val id: String,
    val name: String,
    val platform: String, // "google_drive" or "dropbox"
    val format: String,   // "pdf", "docx", "txt"
    val size: String,
    val modifiedDate: String,
    val content: String,
    val pageCount: Long
)

class CloudStorageRepository {

    fun getGoogleDriveFiles(): List<CloudFileItem> {
        return listOf(
            CloudFileItem(
                id = "gdrive_001",
                name = "Enterprise_AI_Security_Policy_2026.pdf",
                platform = "google_drive",
                format = "pdf",
                size = "2.4 MB",
                modifiedDate = "Yesterday at 4:15 PM",
                pageCount = 18,
                content = """
                    ENTERPRISE AI SECURITY POLICY & COMPLIANCE STANDARD (VERSION 4.2)
                    Document ID: SEC-2026-AI-09
                    Effective Date: January 15, 2026
                    Department: Global Information Security & Governance

                    1. EXECUTIVE SUMMARY & SCOPE
                    This policy establishes the mandatory standards for deploying and utilizing Large Language Models (LLMs) and generative intelligence tools across enterprise departments. All remote and on-premise developers, data analysts, and executive staff must adhere to zero-trust verification procedures when processing confidential operational data.
                    Page 1: Scope covers all production cloud environments, local employee workstations, and edge endpoints.

                    2. ENCRYPTION AT REST AND IN TRANSIT
                    Section 2.1: Data Transmission Security
                    All API communications with generative AI backends must be encapsulated within TLS 1.3 tunnels with certificate pinning enforced on mobile and web gateways. Unencrypted telemetry or logging is strictly prohibited.
                    Section 2.2: Local Offline Storage Protection
                    When documents are cached on mobile devices for offline access, local storage MUST employ AES-256-GCM encryption with device hardware keystore integration. Local SQLite or Room databases containing cached insights must maintain zero plaintext leakage.

                    3. ACCESS CONTROL AND ROLE-BASED PRIVILEGES (RBAC)
                    Section 3.1: Granular Permissions
                    Access levels are segregated into Owner, Editor, and Viewer. Viewers possess read-only privileges and may not export raw source dumps without executive escalation. Editors may append annotations, collaborative comments, and request AI summarization.
                    Section 3.2: Immediate Revocation
                    Upon collaborator removal by the document Owner, session tokens and cached local keys must invalidate within 180 seconds.

                    4. AUDIT TRAILS & COMPLIANCE ARCHIVING
                    Section 4.1: Mandatory Logging
                    Every inference execution, source extraction, user comment, and permission change must emit an immutable audit log containing user ID, ISO-8601 timestamp, and action description. Logs must be archived for a minimum duration of 7 years to satisfy SOC2 Type II and HIPAA requirements.

                    5. MODEL VERIFICATION AND CITATION ACCURACY
                    Section 5.1: Source Attribution Mandate
                    No AI-generated summary or analytical finding may be presented to executive stakeholders without corresponding citation tags referencing the exact page number, section paragraph, or source excerpt. Hallucination checks must run prior to report finalization.
                """.trimIndent()
            ),
            CloudFileItem(
                id = "gdrive_002",
                name = "Remote_Team_Collaboration_Q3_Roadmap.docx",
                platform = "google_drive",
                format = "docx",
                size = "1.1 MB",
                modifiedDate = "Oct 24, 2026",
                pageCount = 9,
                content = """
                    REMOTE PRODUCTIVITY & ASYNCHRONOUS WORKFLOW ROADMAP
                    Q3-Q4 2026 STRATEGY DOCUMENT
                    Lead Author: Director of Remote Operations

                    SECTION 1: OVERVIEW & TEAM CHALLENGES
                    Distributed engineering and product teams across 14 time zones report a 38% increase in meeting fatigue. The organization is transitioning to a "Document-First" operating model, prioritizing thorough asynchronous briefings over daily status calls.

                    SECTION 2: REAL-TIME COLLABORATIVE EDITING
                    Page 3, Paragraph 1: Simultaneous document editing and live commenting reduce feedback turnaround from 48 hours to under 35 minutes.
                    Page 3, Paragraph 4: Threaded comments with resolved state markers enable project managers to track outstanding stakeholder feedback without scheduling alignment meetings.

                    SECTION 3: OFFLINE CAPABILITIES FOR FREQUENT TRAVELERS
                    Page 5, Section 3.2: Over 42% of executive staff travel weekly. Offline mode with automatic queue synchronization ensures uninterrupted reading, insight creation, and comment drafting on flights without connectivity.
                    Page 6: Conflict resolution uses a Last-Write-Wins (LWW) timestamp rule anchored by server timestamps upon reconnection.

                    SECTION 4: KEY PERFORMANCE INDICATORS (KPIs)
                    - Reduce document review latency by 65%.
                    - Achieve 99.9% data sync reliability across Android and web clients.
                    - Maintain zero security breaches across all shared team assets.
                """.trimIndent()
            )
        )
    }

    fun getDropboxFiles(): List<CloudFileItem> {
        return listOf(
            CloudFileItem(
                id = "dropbox_001",
                name = "Financial_Auditing_Annual_Report_FY26.pdf",
                platform = "dropbox",
                format = "pdf",
                size = "4.8 MB",
                modifiedDate = "Oct 22, 2026",
                pageCount = 32,
                content = """
                    GLOBAL HOLDINGS CORP - ANNUAL FINANCIAL & RISK AUDIT REPORT
                    Fiscal Year 2026 (Audited Financial Statements)

                    1. FINANCIAL PERFORMANCE HIGHLIGHTS
                    Page 4, Table 1.1: Consolidated net revenue reached $842.6M, representing a 28.4% year-over-year increase driven by recurring enterprise SaaS subscriptions.
                    Page 7, Section 1.4: Operating cash flow expanded to $214.2M, providing robust liquidity for strategic R&D expansion and edge cloud investments.

                    2. RISK ASSESSMENT & REGULATORY COMPLIANCE
                    Page 12, Paragraph 3: Risk assessment identified interest rate volatility and cross-border currency fluctuations as primary market risks. Hedging contracts mitigated 85% of foreign exchange variance.
                    Page 15, Section 2.8: Regulatory compliance audit confirmed 100% adherence to financial privacy statutes, with zero critical audit deficiencies reported by independent auditors.

                    3. CAPITAL ALLOCATION & FUTURE PROJECTIONS
                    Page 21: R&D expenditure will increase by 15% in FY27, focusing on AI document automation, zero-trust cloud infrastructure, and localized cryptographic data protection.
                """.trimIndent()
            ),
            CloudFileItem(
                id = "dropbox_002",
                name = "Clinical_Study_BioMetric_Sensors.txt",
                platform = "dropbox",
                format = "txt",
                size = "340 KB",
                modifiedDate = "Oct 19, 2026",
                pageCount = 6,
                content = """
                    BIOMETRIC AUTHENTICATION & DEVICE SENSORY SECURITY STUDY
                    Phase III Clinical & Usability Evaluation

                    Abstract:
                    Evaluating mobile biometric recognition (Class 3 biometric sensors including fingerprint and 3D facial authentication) for safeguarding high-value enterprise records.

                    Findings:
                    1. Authentication speed: Average unlock latency of 185 milliseconds.
                    2. Security barrier: False Acceptance Rate (FAR) under 0.0001%, offering significantly higher defense against unauthorized local device tampering compared to 4-digit PINs.
                    3. User adoption: 94.2% of test subjects reported feeling confident in storing sensitive legal and financial summaries when biometric locks are engaged.
                """.trimIndent()
            )
        )
    }
}
