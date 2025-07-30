package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StepBarGraph (
    modifier: Modifier = Modifier,
    chartDataList: List<StepData>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    barWidthFraction: Float = 0.7f,
    maxBarHeight: Dp = 150.dp
) {
    if(chartDataList.isEmpty()){
        Text("No data available for graph.", modifier = modifier.padding(16.dp))
        return
    }
    val maxSteps = chartDataList.maxOfOrNull {it.steps} ?: 1L
    Row(modifier = modifier
        .fillMaxWidth()
        .height(maxBarHeight + 80.dp) // maxBarHeight + (!!! this is a problem) hardcoded space for labels
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
    ){
       chartDataList.forEach{ data ->
           Column(horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.Bottom,
               modifier = Modifier.weight(1f))
           {
               val barHeightRatio = data.steps.toFloat() / maxSteps
               val barHeightDp = maxBarHeight * barHeightRatio
               // Bar
               Canvas(modifier = Modifier
                   .fillMaxWidth(barWidthFraction)
                   .height(barHeightDp)
                   ){
                   drawRect(color = barColor)
               }
               Spacer(modifier = Modifier.height(4.dp))
               Text(text = data.date, color = labelColor)
           }

       }
    }
}