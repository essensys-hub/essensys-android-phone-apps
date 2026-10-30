package com.essensys.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.essensys.android.ui.EssensysRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as EssensysApp).container
        setContent { EssensysRoot(container) }
    }

    /** Journal du support écrit sur disque à la mise en arrière-plan (design D1). */
    override fun onStop() {
        super.onStop()
        (application as EssensysApp).container.appLog.flush()
    }
}
