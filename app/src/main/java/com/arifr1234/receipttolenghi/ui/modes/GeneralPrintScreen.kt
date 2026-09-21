package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.components.PrintableCard
import com.arifr1234.receipttolenghi.ui.components.PrintableCarousel
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralPrintScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspace") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        // We explicitly use a fresh Modifier here. 
        // MainActivity passes a modifier with .padding(SystemBars) which causes double-padding 
        // on both the top (TopAppBar) and the bottom (keyboard/navbar).
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Only take the top padding (TopAppBar + Status Bar) from Scaffold
                .padding(top = innerPadding.calculateTopPadding())
                // Use safeDrawing for the bottom to handle both NavBar and Keyboard smoothly
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                val history by PlaylistManager.history.collectAsState()
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                    reverseLayout = true
                ) {
                    items(history, key = { it.id }) { item ->
                        PrintableCard(
                            modifier = Modifier.fillMaxWidth(),
                            state = item.state,
                            definition = PrintableRegistry.getTypedDefinitionFor(item.state),
                            isReadOnly = true,
                            onPrintSuccess = { printedState ->
                                PlaylistManager.addStateToHistory(printedState)
                            }
                        )
                    }
                }
                
                PrintableCarousel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}
