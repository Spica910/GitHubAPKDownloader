package com.github.apkdownloader.ai

import java.util.Date

/**
 * Result of a build attempt
 */
data class BuildResult(
    val success: Boolean,
    val apkPath: String? = null,
    val errorLog: String? = null,
    val buildTime: Long = 0, // milliseconds
    val timestamp: Date = Date()
)

/**
 * Result of an AI fix attempt
 */
data class FixResult(
    val success: Boolean,
    val aiUsed: String, // "Gemini" or "Claude"
    val changesApplied: Boolean,
    val description: String,
    val filesModified: List<String> = emptyList(),
    val fixSuggestion: String? = null,
    val error: String? = null
)

/**
 * AI tier for cost-effective fixing
 */
enum class AiTier(val displayName: String, val cost: String) {
    GEMINI("Gemini", "Free"),
    CLAUDE("Claude", "Paid")
}

/**
 * Build history item for tracking
 */
data class BuildHistory(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Date = Date(),
    val branch: String,
    val buildSuccess: Boolean,
    val aiUsed: String? = null,
    val errorCount: Int = 0,
    val fixesApplied: Int = 0,
    val buildTime: Long = 0,
    val apkPath: String? = null,
    val prUrl: String? = null
)

/**
 * Build status for UI updates
 */
sealed class BuildStatus {
    object Idle : BuildStatus()
    object Syncing : BuildStatus()
    object Building : BuildStatus()
    data class AiFixing(val tier: AiTier, val attempt: Int) : BuildStatus()
    object CreatingPr : BuildStatus()
    data class Success(val apkPath: String, val buildTime: Long) : BuildStatus()
    data class Failed(val error: String) : BuildStatus()
}

/**
 * AI fix request
 */
data class AiFixRequest(
    val errorLog: String,
    val buildGradleContent: String?,
    val previousFixes: List<String> = emptyList()
)

/**
 * Configuration for AI build
 */
data class AiBuildConfig(
    val useGeminiFirst: Boolean = true,
    val fallbackToClaude: Boolean = true,
    val maxRetries: Int = 3,
    val autoInstall: Boolean = true,
    val createPrOnFix: Boolean = true,
    val cleanBuild: Boolean = false
)
