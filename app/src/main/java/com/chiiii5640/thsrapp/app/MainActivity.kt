package com.chiiii5640.thsrapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiiii5640.thsrapp.features.searchDashboard.SearchDashboardScreen
import com.chiiii5640.thsrapp.features.searchDashboard.SearchDashboardViewModel
import com.chiiii5640.thsrapp.ui.theme.ThsrAppTheme

class MainActivity : ComponentActivity() {
    private val graph by lazy { AppGraph(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThsrAppTheme {
                val viewModel: SearchDashboardViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            graph.searchDashboardViewModel() as T
                    },
                )
                SearchDashboardScreen(viewModel)
            }
        }
    }
}
