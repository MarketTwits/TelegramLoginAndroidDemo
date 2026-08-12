package com.markettwits.devx.tgsignin.data.dataSource

import com.markettwits.devx.tgsignin.data.model.AppLinkVerification

interface AppLinkVerificationDataSource {
    suspend fun checkVerification(): AppLinkVerification
}
