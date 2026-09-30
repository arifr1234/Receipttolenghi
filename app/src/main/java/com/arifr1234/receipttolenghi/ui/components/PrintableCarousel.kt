package com.arifr1234.receipttolenghi.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableRegistry
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableState
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintableCarousel(
    modifier: Modifier = Modifier,
    onPrintSuccess: (PrintableState) -> Unit = {}
) {
    val types = PrintableType.entries
    val pagerState = rememberPagerState(pageCount = { types.size })
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Preserve draft states for each definition so user doesn't lose input when swiping
    val draftStates = remember {
        mutableStateMapOf<PrintableType, PrintableState>().apply {
            types.forEach { type ->
                put(type, type.definition.createDefaultState())
            }
        }
    }
    
    // Dismiss keyboard and clear focus when swiping to a different card
    LaunchedEffect(pagerState.currentPage) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    Column(modifier = modifier) {
        // Text Tabs
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            edgePadding = 8.dp,
            divider = {}
        ) {
            types.forEachIndexed { index, type ->
                Tab(
                    selected = pagerState.currentPage == index,
                    modifier = Modifier.height(32.dp),
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { 
                        Text(
                            text = type.definition.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Swipeable Carousel
        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(alignment = Alignment.BottomCenter),
            verticalAlignment = Alignment.Bottom
        ) { page ->
            PrintableCard(
                modifier = Modifier.fillMaxWidth(),
                state = draftStates[types[page]] ?: return@HorizontalPager,
                onStateChange = { newState ->
                    draftStates[types[page]] = newState
                },
                onPrintSuccess = onPrintSuccess,
                definition = PrintableRegistry.getTypedDefinitionFor(types[page]),
                clearAfterPrint = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dot Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(types.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }
    }
}
