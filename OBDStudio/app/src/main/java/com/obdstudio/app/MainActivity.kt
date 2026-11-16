package com.obdstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.obdstudio.app.core.DiagnosticController
import com.obdstudio.app.ui.StudioApp

class MainActivity : ComponentActivity() {
    private lateinit var controller: DiagnosticController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = DiagnosticController(applicationContext)
        setContent { StudioApp(controller) }
    }

    override fun onDestroy() {
        controller.dispose()
        super.onDestroy()
    }
}
