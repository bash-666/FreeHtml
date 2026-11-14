package com.freehtml.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RecentFile(
    val uri: String,
    val displayName: String,
    val type: DocumentType,
    val openedAt: Long = System.currentTimeMillis(),
)

fun List<RecentFile>.withoutUris(uris: Set<String>): List<RecentFile> =
    if (uris.isEmpty()) this else filterNot { it.uri in uris }
