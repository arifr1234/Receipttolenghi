package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FontSelectorDialog(
    onDismiss: () -> Unit,
    onFontSelected: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    var fonts by remember { mutableStateOf<List<GoogleFontFamily>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

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
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Select Font",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (fonts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Failed to load fonts (offline).")
                }
            } else {
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
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    flingBehavior = highFrictionFling
                ) {
                    items(fonts, key = { it.family }) { font ->
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
