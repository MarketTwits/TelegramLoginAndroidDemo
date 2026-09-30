package com.markettwits.devx.tgsignin.data.datasource

import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.SignalUnknownCredentialRequest
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import com.markettwits.devx.tgsignin.data.repository.toPasskeyError
import org.json.JSONObject

interface PasskeyCredentialDataSource {
    val isSupported: Boolean
    suspend fun create(context: Context, requestJson: String): String
    suspend fun get(context: Context, requestJson: String): String
    suspend fun clearState()
    suspend fun signalUnknownCredential(rpId: String, credentialId: String)
}

class PasskeyCredentialDataSourceImpl(
    private val context: Context
) : PasskeyCredentialDataSource {
    private val manager by lazy { CredentialManager.create(context) }

    override val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    @android.annotation.SuppressLint("PublicKeyCredential")
    override suspend fun create(context: Context, requestJson: String): String {
        try {
            val result = manager.createCredential(
                context = context,
                request = CreatePublicKeyCredentialRequest(requestJson)
            )
            return (result as CreatePublicKeyCredentialResponse).registrationResponseJson
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toPasskeyError()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    override suspend fun get(context: Context, requestJson: String): String {
        try {
            val result = manager.getCredential(
                context = context,
                request = GetCredentialRequest(
                    credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson))
                )
            )
            return (result.credential as PublicKeyCredential).authenticationResponseJson
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toPasskeyError()
        }
    }

    override suspend fun clearState() {
        manager.clearCredentialState(ClearCredentialStateRequest())
    }

    override suspend fun signalUnknownCredential(rpId: String, credentialId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        manager.signalCredentialState(
            SignalUnknownCredentialRequest(
                JSONObject().put("rpId", rpId).put("credentialId", credentialId).toString()
            )
        )
    }
}
