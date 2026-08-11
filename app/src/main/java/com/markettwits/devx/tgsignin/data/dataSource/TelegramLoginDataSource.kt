package com.markettwits.devx.tgsignin.data.dataSource

import android.content.Context
import android.net.Uri
import com.markettwits.devx.tgsignin.data.model.TelegramScope

interface TelegramLoginDataSource {
    fun startLogin(context: Context, scopes: Set<TelegramScope>)
    suspend fun consumeCallback(uri: Uri): String
    fun isTelegramCallback(uri: Uri): Boolean
}

class TelegramLoginException(message: String) : Exception(message)
class TelegramConfigurationException : Exception()
