package com.arifr1234.receipttolenghi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableDefinition
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableState

@Composable
fun <T : PrintableState> PrintableCard(
    definition: PrintableDefinition<T>,
    state: T,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isConfigExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(definition.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            // The dynamic UI editor for this state
            definition.EditorUI(state)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { isConfigExpanded = !isConfigExpanded }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure",
                        tint = if (isConfigExpanded) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(onClick = { /* Add to playlist action for later */ }) {
                    Icon(Icons.Default.Add, contentDescription = "Add to playlist")
                }
                IconButton(
                    onClick = {
                        PrinterManager.printAsync(context) { printer ->
                            definition.print(state, printer)
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Print")
                }
            }
            
            AnimatedVisibility(
                visible = isConfigExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    definition.ConfigUI(state)
                }
            }
        }
    }
}
