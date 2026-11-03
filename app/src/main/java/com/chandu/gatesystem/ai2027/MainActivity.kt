package com.chandu.gatesystem.ai2027

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.chandu.gatesystem.ai2027.ui.GateNav
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.theme.GateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GateApp).container
        // A tapped alarm notification carries NotificationHelper.EXTRA_ALARM; the app opens on its normal start flow
        // (init -> dashboard), where the day's quests are the first thing shown.
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                GateTheme { GateNav() }
            }
        }
    }
}
