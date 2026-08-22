package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.components.PrintableCard
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableDefinition
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableRegistry
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralPrintScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val definitions = PrintableRegistry.allDefinitions
    val pagerState = rememberPagerState(pageCount = { definitions.size })
    val coroutineScope = rememberCoroutineScope()

    // Preserve draft states for each definition so user doesn't lose input when swiping
    val draftStates = remember {
        definitions.associateWith { it.createDefaultState() }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        
        Text("Select Printable", style = MaterialTheme.typography.titleLarge)
        
        // Push the entire selection section to the bottom
        Spacer(modifier = Modifier.weight(1f))

        // Text Tabs
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            divider = {}
        ) {
            definitions.forEachIndexed { index, def ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { 
                        Text(
                            text = def.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Swipeable Carousel
        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(alignment = Alignment.BottomCenter),
            verticalAlignment = Alignment.Bottom
        ) { page ->
            @Suppress("UNCHECKED_CAST")
            val definition = definitions[page] as PrintableDefinition<PrintableState>
            val state = draftStates[definitions[page]]!!
            
            PrintableCard(
                definition = definition,
                state = state,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dot Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(definitions.size) { iteration ->
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

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Home")
        }
    }
}
