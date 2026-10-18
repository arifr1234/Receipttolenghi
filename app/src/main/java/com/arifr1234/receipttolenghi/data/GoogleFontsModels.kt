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
    val tags: List<FontTag>? = null
) {
    val isColorFont: Boolean
        get() = tags?.any { it.name == "/Technology/Color" } == true

    val significantTags: Map<String, List<String>>
        get() {
            val result = mutableMapOf<String, MutableList<String>>()
            tags?.filter { (it.weight ?: 0.0) >= 50.0 }?.forEach { tag ->
                val parts = tag.name.split("/")
                if (parts.size >= 3) {
                    val category = parts[1]
                    val tagName = parts[2]
                    result.getOrPut(category) { mutableListOf() }.add(tagName)
                }
            }
            return result
        }
}

data class FontTag(
    val name: String,
    val weight: Double? = null
)
