package com.markettwits.devx.tgsignin.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.markettwits.devx.tgsignin.BuildConfig
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.AppearanceLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.AppearanceLocalDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.BackendReadinessDataSource
import com.markettwits.devx.tgsignin.data.dataSource.BackendReadinessDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.CryptoManager
import com.markettwits.devx.tgsignin.data.dataSource.CryptoManagerImpl
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSourceImpl
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.AppearanceRepository
import com.markettwits.devx.tgsignin.data.repository.AppearanceRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepository
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepositoryImpl
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.viewModel.AppearanceViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.BackendReadinessViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.LoginScreenViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.ProfileScreenViewModel

private val ioDispatcherQualifier = named("ioDispatcher")
private val applicationScopeQualifier = named("applicationScope")

val appModule = module {
    single {
        TelegramLoginConfig(
            clientId = BuildConfig.TELEGRAM_CLIENT_ID,
            redirectUri = BuildConfig.TELEGRAM_REDIRECT_URI,
            redirectHost = BuildConfig.TELEGRAM_REDIRECT_HOST,
            backendUrl = BuildConfig.TELEGRAM_BACKEND_URL
        )
    }
    single<CoroutineDispatcher>(ioDispatcherQualifier) { Dispatchers.IO }
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(ioDispatcherQualifier))
    }
    single<CryptoManager> { CryptoManagerImpl() }
    single<TelegramLoginDataSource> { TelegramLoginDataSourceImpl(get()) }
    single<TelegramAuthApiDataSource> {
        TelegramAuthApiDataSourceImpl(
            config = get(),
            ioDispatcher = get(ioDispatcherQualifier)
        )
    }
    single<BackendReadinessDataSource> {
        BackendReadinessDataSourceImpl(
            config = get(),
            ioDispatcher = get(ioDispatcherQualifier)
        )
    }
    single<BackendReadinessRepository> { BackendReadinessRepositoryImpl(get()) }
    single<AuthenticationLocalDataSource> { AuthenticationLocalDataSourceImpl(androidContext(), get()) }
    single<AuthenticationRepository> {
        AuthenticationRepositoryImpl(
            telegramLoginDataSource = get(),
            telegramAuthApiDataSource = get(),
            authenticationLocalDataSource = get(),
            applicationScope = get(applicationScopeQualifier)
        )
    }
    single<AppearanceLocalDataSource> { AppearanceLocalDataSourceImpl(androidContext()) }
    single<AppearanceRepository> { AppearanceRepositoryImpl(get()) }
    viewModel { LoginScreenViewModel(get()) }
    viewModel { ProfileScreenViewModel(get()) }
    viewModel { AppearanceViewModel(get()) }
    viewModel { BackendReadinessViewModel(get()) }
}
