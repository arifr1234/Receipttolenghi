package com.arifr1234.receipttolenghi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.printer.DitheringAlgorithm
import com.arifr1234.receipttolenghi.utils.BrightnessCurve
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DitheringAlgorithmSelector(
    selectedAlgorithm: DitheringAlgorithm,
    onAlgorithmSelected: (DitheringAlgorithm) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Dithering Algorithm", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                DitheringAlgorithm.entries.forEach { algo ->
                    FilterChip(
                        selected = selectedAlgorithm == algo,
                        onClick = { onAlgorithmSelected(algo) },
                        label = { Text(algo.label) },
                    )
                }
            }
        }
    }
}

@Composable
fun BrightnessCompensationSelector(
    brightnessMultiplier: Float,
    onMultiplierChange: (Float) -> Unit,
    brightnessCurve: BrightnessCurve,
    onCurveChange: (BrightnessCurve) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBrightnessInfo by remember { mutableStateOf(value = false) }

    if (showBrightnessInfo) {
        AlertDialog(
            onDismissRequest = { showBrightnessInfo = false },
            confirmButton = {
                TextButton(onClick = { showBrightnessInfo = false }) {
                    Text("OK")
                }
            },
            title = { Text("Brightness Compensation") },
            text = { Text("Thermally printed images tend to appear darker.") },
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Brightness (%.1fx)".format(Locale.US, brightnessMultiplier),
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = { showBrightnessInfo = true },
                modifier = Modifier
                    .size(28.dp)
                    .padding(start = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Brightness Info",
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BrightnessCurve.entries.forEach { curve ->
                        FilterChip(
                            selected = brightnessCurve == curve,
                            onClick = { onCurveChange(curve) },
                            label = { Text(curve.label) },
                        )
                    }
                }
            }
        }
        Slider(
            value = brightnessMultiplier,
            onValueChange = { onMultiplierChange((it * 10f).roundToInt() / 10f) },
            valueRange = 1f..4f,
            steps = 29,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
