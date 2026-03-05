package com.rohittp.pair

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rohittp.pair.ui.BluePairApp
import com.rohittp.pair.ui.theme.BluePairTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluePairTheme {
                BluePairApp()
            }
        }
    }
}
