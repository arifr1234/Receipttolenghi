package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FontSelectorDialog(
    onDismiss: () -> Unit,
    onFontSelected: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    selectedSubsets: Set<String> = emptySet(),
    onSelectedSubsetsChange: (Set<String>) -> Unit = {},
    selectedTags: Map<String, String> = emptyMap(),
    onSelectedTagsChange: (Map<String, String>) -> Unit = {}
) {
    var fonts by remember { mutableStateOf<List<GoogleFontFamily>>(emptyList()) }
    var subsets by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagsByCategory by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
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
            tagsByCategory = GoogleFontsRepository.getTagsByCategory()
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
                .fillMaxHeight(1f)
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

            SubsetSelector(
                subsets = subsets,
                selectedSubsets = selectedSubsets,
                onSelectedSubsetsChange = onSelectedSubsetsChange,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            tagsByCategory.forEach { (category, tags) ->
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp))
                        tags.forEach { tag ->
                            FilterChip(
                                selected = selectedTags[category] == tag,
                                onClick = { 
                                    if (selectedTags[category] == tag) {
                                        onSelectedTagsChange(selectedTags - category)
                                    } else {
                                        onSelectedTagsChange(selectedTags + (category to tag))
                                    }
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (fonts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Failed to load fonts (offline).")
                }
            } else {
                val filteredFonts = remember(fonts, searchQuery, selectedSubsets, selectedTags) {
                    fonts.filter { font -> 
                        val matchesSearch = searchQuery.isBlank() || font.family.contains(searchQuery, ignoreCase = true)
                        val matchesSubsets = selectedSubsets.all { it in font.subsets }
                        
                        // Check if the font has all the selected tags
                        val matchesTags = selectedTags.all { (category, tag) ->
                            font.significantTags[category]?.contains(tag) == true
                        }
                        
                        matchesSearch && matchesSubsets && matchesTags
                    }
                }

                LaunchedEffect(searchQuery, selectedSubsets, selectedTags) {
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
                
                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            // Consume the remaining scroll so the bottom sheet doesn't dismiss
                            return available
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection),
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
                                .padding(vertical = 8.dp),
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubsetSelector(
    subsets: List<String>,
    selectedSubsets: Set<String>,
    onSelectedSubsetsChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    // TODO: This composable is more complicated then I wished it was.

    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            selectedSubsets.forEach { subset ->
                InputChip(
                    selected = true,
                    onClick = { onSelectedSubsetsChange(selectedSubsets - subset) },
                    label = { Text(subset) },
                    trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            
            androidx.compose.foundation.text.BasicTextField(
                value = searchText,
                onValueChange = { 
                    searchText = it
                    expanded = true 
                },
                modifier = Modifier
                    .widthIn(min = 120.dp)
                    .align(Alignment.CenterVertically),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                    ) {
                        if (searchText.isEmpty()) {
                            Text(
                                "+ Subset filter",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        
        val unselected = remember(subsets, selectedSubsets) { subsets.filter { it !in selectedSubsets } }
        val filteredDropdown = remember(unselected, searchText) {
            if (searchText.isBlank()) unselected else unselected.filter { it.contains(searchText, ignoreCase = true) }
        }
        
        if (filteredDropdown.isNotEmpty() && expanded) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredDropdown.forEach { subset ->
                    DropdownMenuItem(
                        text = { Text(subset) },
                        onClick = {
                            onSelectedSubsetsChange(selectedSubsets + subset)
                            searchText = ""
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
