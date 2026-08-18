package com.markettwits.devx.tgsignin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.markettwits.devx.tgsignin.ui.TelegramLoginApp
import com.markettwits.devx.tgsignin.ui.viewmodel.LoginViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModel()
    private var wentToBackgroundDuringLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeTelegramIntent(intent)
        setContent {
            TelegramLoginApp(
                loginViewModel = loginViewModel,
                onLogin = { loginViewModel.login(this@MainActivity) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTelegramIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        wentToBackgroundDuringLogin = true
    }

    override fun onResume() {
        super.onResume()
        if (wentToBackgroundDuringLogin) {
            loginViewModel.cancelIfAwaitingCallback()
            wentToBackgroundDuringLogin = false
        }
    }

    private fun consumeTelegramIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        loginViewModel.consumeCallback(uri)
        intent.data = null
    }
}
