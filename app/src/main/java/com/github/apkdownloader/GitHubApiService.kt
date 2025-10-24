package com.github.apkdownloader

import retrofit2.Response
import retrofit2.http.*

interface GitHubApiService {

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") token: String
    ): Response<User>

    @GET("user/repos")
    suspend fun getUserRepositories(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "updated",
        @Query("type") type: String = "all"
    ): Response<List<Repository>>

    @GET("search/repositories")
    suspend fun searchRepositories(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 50
    ): Response<RepositorySearchResponse>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getRepositoryReleases(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): Response<List<Release>>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<okhttp3.ResponseBody>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getRepositoryBranches(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<List<GitBranch>>

    @GET("repos/{owner}/{repo}/git/trees/{tree_sha}")
    suspend fun getGitTree(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tree_sha") treeSha: String,
        @Query("recursive") recursive: Int = 1
    ): Response<GitTreeResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): Response<GitFileContent>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateFileRequest
    ): Response<CreateFileResponse>

    @POST("user/repos")
    suspend fun createRepository(
        @Header("Authorization") token: String,
        @Body request: CreateRepositoryRequest
    ): Response<Repository>
}
