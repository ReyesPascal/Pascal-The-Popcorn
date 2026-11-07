package com.arflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.ui.theme.ArflixTypography
import kotlinx.coroutines.delay
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import java.util.Date

/**
 * Top bar clock display
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopBarClock(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(getCurrentTime(context)) }

    LaunchedEffect(context) {
        while (true) {
            currentTime = getCurrentTime(context)
            val now = System.currentTimeMillis()
            val delayToNextMinute = 60_000L - (now % 60_000L)
            delay(delayToNextMinute.coerceIn(1_000L, 60_000L))
        }
    }

    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {


            // Clock
            Text(
                text = currentTime,
                style = ArflixTypography.clock,
                color = Color.White
            )
        }
    }
}

/**
 * Formats the current clock time
 */


private fun getCurrentTime(context: Context): String {
    val timeFormat = DateFormat.getTimeFormat(context)
    return timeFormat.format(Date())
}
