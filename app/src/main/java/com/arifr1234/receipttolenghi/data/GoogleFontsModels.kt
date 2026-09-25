package com.arifr1234.receipttolenghi.data

data class GoogleFontsResponse(
    val kind: String,
    val items: List<GoogleFontFamily>
)

data class GoogleFontFamily(
    val family: String,
    val variants: List<String>,
    val subsets: List<String>,
    val version: String,
    val lastModified: String,
    val files: Map<String, String>,
    val category: String,
    val kind: String,
    val menu: String,
    val tags: List<String>? = null
)
