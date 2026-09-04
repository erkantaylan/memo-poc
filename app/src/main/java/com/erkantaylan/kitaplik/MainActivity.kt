package com.erkantaylan.kitaplik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.erkantaylan.kitaplik.ui.CatalogScreen
import com.erkantaylan.kitaplik.ui.CatalogViewModel
import com.erkantaylan.kitaplik.ui.theme.KitaplikTheme
import com.erkantaylan.kitaplik.ui.theme.Palette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            KitaplikTheme {
                val vm: CatalogViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { CatalogViewModel(catalogSource) }
                    }
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Palette.bg)
                        .safeDrawingPadding()
                ) {
                    CatalogScreen(vm)
                }
            }
        }
    }
}
