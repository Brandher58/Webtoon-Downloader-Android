package com.brandher.webtoondl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brandher.webtoondl.ui.navigation.WebtoonDLApp
import com.brandher.webtoondl.ui.theme.WebtoonDLTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebtoonDLTheme {
                WebtoonDLApp()
            }
        }
    }
}