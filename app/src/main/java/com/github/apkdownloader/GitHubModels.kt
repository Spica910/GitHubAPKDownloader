package com.github.apkdownloader

import com.google.gson.annotations.SerializedName

data class AccessTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val scope: String
)

data class Repository(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    val language: String?,
    val owner: Owner,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("clone_url") val cloneUrl: String,
    val private: Boolean = false
)

data class Owner(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String
)

data class RepositorySearchResponse(
    val items: List<Repository>
)

data class Release(
    val id: Long,
    val name: String?,
    @SerializedName("tag_name") val tagName: String,
    val body: String?,
    val assets: List<Asset>,
    @SerializedName("published_at") val publishedAt: String
)

data class Asset(
    val id: Long,
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    val size: Long,
    @SerializedName("content_type") val contentType: String
)

data class User(
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String
)

data class GitTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<GitTreeItem>,
    val truncated: Boolean
)

data class GitTreeItem(
    val path: String,
    val mode: String,
    val type: String,  // "blob" for file, "tree" for directory
    val sha: String,
    val size: Long?,
    val url: String
)

data class GitFileContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val url: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("download_url") val downloadUrl: String?,
    val type: String,
    val content: String?,
    val encoding: String?
)

data class GitBranch(
    val name: String,
    val commit: GitCommit,
    val protected: Boolean
)

data class GitCommit(
    val sha: String,
    val url: String
)

data class CreateFileRequest(
    val message: String,
    val content: String,
    val branch: String?
)

data class CreateFileResponse(
    val content: GitFileContent,
    val commit: CommitInfo
)

data class CommitInfo(
    val sha: String,
    val message: String
)

data class CreateRepositoryRequest(
    val name: String,
    val description: String? = null,
    @SerializedName("private") val isPrivate: Boolean = false,
    @SerializedName("auto_init") val autoInit: Boolean = true
)

data class RepositoryWithApk(
    val repository: Repository,
    val apkInfo: ApkInfo? = null,
    val artifactApks: List<ApkInfo> = emptyList()
)

data class ApkInfo(
    val fileName: String,
    val downloadUrl: String,
    val location: String,
    val size: Long,
    val source: ApkSource = ApkSource.RELEASE,
    var packageInfo: ApkPackageInfo? = null,
    var updateInfo: ApkUpdateInfo? = null
)

enum class ApkSource {
    RELEASE,
    ARTIFACT
}

data class ArtifactsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val artifacts: List<Artifact>
)

data class Artifact(
    val id: Long,
    val name: String,
    @SerializedName("size_in_bytes") val sizeInBytes: Long,
    @SerializedName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("expires_at") val expiresAt: String
)
