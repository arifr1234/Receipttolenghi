package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.components.PrintableCarousel

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
                    .verticalScroll(rememberScrollState())
            ) {
                PrintableCarousel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}
