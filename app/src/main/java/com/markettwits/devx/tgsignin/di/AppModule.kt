package com.markettwits.devx.tgsignin.di

import com.markettwits.devx.tgsignin.BuildConfig
import com.markettwits.devx.tgsignin.data.dataSource.AppLinkVerificationDataSource
import com.markettwits.devx.tgsignin.data.dataSource.AppLinkVerificationDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.AppearanceLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.AppearanceLocalDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSource
import com.markettwits.devx.tgsignin.data.dataSource.AuthenticationLocalDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.BackendReadinessDataSource
import com.markettwits.devx.tgsignin.data.dataSource.BackendReadinessDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.CryptoManager
import com.markettwits.devx.tgsignin.data.dataSource.CryptoManagerImpl
import com.markettwits.devx.tgsignin.data.dataSource.ProfileEmojiRemoteDataSource
import com.markettwits.devx.tgsignin.data.dataSource.ProfileEmojiRemoteDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramAuthApiDataSourceImpl
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSource
import com.markettwits.devx.tgsignin.data.dataSource.TelegramLoginDataSourceImpl
import com.markettwits.devx.tgsignin.data.repository.AppLinkVerificationRepository
import com.markettwits.devx.tgsignin.data.repository.AppLinkVerificationRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.AppearanceRepository
import com.markettwits.devx.tgsignin.data.repository.AppearanceRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepository
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepository
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepositoryImpl
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.viewModel.AppLinkVerificationViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.AppearanceViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.BackendReadinessViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.LoginScreenViewModel
import com.markettwits.devx.tgsignin.ui.viewModel.ProfileScreenViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val ioDispatcherQualifier = named("ioDispatcher")
private val applicationScopeQualifier = named("applicationScope")

val appModule = module {
    single {
        TelegramLoginConfig(
            clientId = BuildConfig.TELEGRAM_CLIENT_ID,
            redirectUri = BuildConfig.TELEGRAM_REDIRECT_URI,
            redirectHost = BuildConfig.TELEGRAM_REDIRECT_HOST,
            backendUrl = BuildConfig.TELEGRAM_BACKEND_URL,
            appToken = BuildConfig.APP_TOKEN
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
    single<ProfileEmojiRemoteDataSource> {
        ProfileEmojiRemoteDataSourceImpl(
            config = get(),
            ioDispatcher = get(ioDispatcherQualifier)
        )
    }
    single<ProfileEmojiRepository> {
        ProfileEmojiRepositoryImpl(
            context = androidContext(),
            remoteDataSource = get(),
            ioDispatcher = get(ioDispatcherQualifier),
            applicationScope = get(applicationScopeQualifier)
        )
    }
    single<AppLinkVerificationDataSource> {
        AppLinkVerificationDataSourceImpl(
            context = androidContext(),
            config = get(),
            ioDispatcher = get(ioDispatcherQualifier)
        )
    }
    single<AppLinkVerificationRepository> { AppLinkVerificationRepositoryImpl(get()) }
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
    viewModel { AppLinkVerificationViewModel(get()) }
}
