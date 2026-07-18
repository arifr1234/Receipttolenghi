package com.arifr1234.receipttolenghi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.arifr1234.receipttolenghi.ui.home.HomeScreen
import com.arifr1234.receipttolenghi.ui.theme.ReceipttolenghiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReceipttolenghiTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        onNavigateToBluetooth = { /* TODO */ },
                        onNavigateToMode = { /* TODO */ }
                    )
                }
            }
        }
    }
}