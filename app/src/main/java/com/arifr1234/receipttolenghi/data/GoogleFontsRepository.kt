package com.arifr1234.receipttolenghi.data

import com.arifr1234.receipttolenghi.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object GoogleFontsRepository {
    private const val API_URL = "https://www.googleapis.com/webfonts/v1/webfonts?key=${BuildConfig.GOOGLE_FONTS_API_KEY}&sort=popularity&capability=FAMILY_TAGS"

    // Cache fonts in memory once loaded
    private var cachedFonts: List<GoogleFontFamily>? = null

    suspend fun getFonts(): List<GoogleFontFamily> = withContext(Dispatchers.IO) {
        // Return memory cache if available
        cachedFonts?.let { return@withContext it }

        val gson = Gson()

        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            
            val response = gson.fromJson(responseText, GoogleFontsResponse::class.java)
            if (response?.items != null) {
                val nonColorFonts = response.items.filterNot { it.isColorFont }
                cachedFonts = nonColorFonts
                return@withContext nonColorFonts
            } else {
                throw Exception("Failed to parse Google Fonts response")
            }
        } else {
            throw Exception("Failed to fetch fonts: ${connection.responseCode} ${connection.responseMessage}")
        }
    }

    suspend fun getSubsetsSortedByPopularity(): List<String> = withContext(Dispatchers.Default) {
        val fonts = getFonts()
        val counts = mutableMapOf<String, Int>()
        for (font in fonts) {
            for (subset in font.subsets) {
                counts[subset] = (counts[subset] ?: 0) + 1
            }
        }
        return@withContext counts.entries.sortedByDescending { it.value }.map { it.key }
    }
    suspend fun getTagsByCategory(): Map<String, List<String>> = withContext(Dispatchers.Default) {
        val fonts = getFonts()
        val interestingCategories = listOf("Expressive", "Sans", "Script", "Serif", "Theme")
        val categoryToTags = mutableMapOf<String, MutableSet<String>>()
        
        interestingCategories.forEach { categoryToTags[it] = mutableSetOf() }

        for (font in fonts) {
            font.significantTags.forEach { (category, tags) ->
                if (category in interestingCategories) {
                    categoryToTags[category]?.addAll(tags)
                }
            }
        }
        
        return@withContext categoryToTags.mapValues { it.value.sorted() }
    }
}
