package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arifr1234.receipttolenghi.R
import com.arifr1234.receipttolenghi.data.GoogleFontFamily
import com.arifr1234.receipttolenghi.data.GoogleFontsRepository
import com.arifr1234.receipttolenghi.utils.AppErrorHandler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FontSelectorDialog(
    onDismiss: () -> Unit,
    onFontSelected: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    selectedSubsets: Set<String> = emptySet(),
    onSelectedSubsetsChange: (Set<String>) -> Unit = {}
) {
    var fonts by remember { mutableStateOf<List<GoogleFontFamily>>(emptyList()) }
    var subsets by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val provider = remember {
        GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs
        )
    }

    LaunchedEffect(Unit) {
        AppErrorHandler.runWithGlobalLog {
            fonts = GoogleFontsRepository.getFonts()
            subsets = GoogleFontsRepository.getSubsetsSortedByPopularity()
            isLoading = false
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Select Font",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    onSearchQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text("Search fonts...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            onSearchQueryChange("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subsets) { subset ->
                    FilterChip(
                        selected = subset in selectedSubsets,
                        onClick = {
                            val newSelected = if (subset in selectedSubsets) {
                                selectedSubsets - subset
                            } else {
                                selectedSubsets + subset
                            }
                            onSelectedSubsetsChange(newSelected)
                        },
                        label = { Text(subset) }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (fonts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Failed to load fonts (offline).")
                }
            } else {
                val filteredFonts = remember(fonts, searchQuery, selectedSubsets) {
                    fonts.filter { font -> 
                        (searchQuery.isBlank() || font.family.contains(searchQuery, ignoreCase = true)) &&
                        selectedSubsets.all { it in font.subsets }
                    }
                }

                LaunchedEffect(searchQuery, selectedSubsets) {
                    listState.scrollToItem(0)
                }

                // High friction fling behavior
                val highFrictionFling = remember {
                    object : FlingBehavior {
                        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                            // Dramatically reduce fling velocity to prevent scrolling past hundreds of fonts
                            val dampen = 0.05f
                            scrollBy(initialVelocity * dampen / 10f)
                            return 0f
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    flingBehavior = highFrictionFling
                ) {
                    items(filteredFonts, key = { it.family }) { font ->
                        val fontFamily = remember(font.family) {
                            FontFamily(
                                Font(googleFont = GoogleFont(font.family), fontProvider = provider)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onFontSelected(font.family)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = font.family,
                                fontFamily = fontFamily,
                                fontSize = 24.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
