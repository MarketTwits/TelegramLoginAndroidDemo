package com.markettwits.devx.tgsignin.di

import com.markettwits.devx.tgsignin.BuildConfig
import com.markettwits.devx.tgsignin.data.datasource.AppLinkVerificationDataSource
import com.markettwits.devx.tgsignin.data.datasource.AppLinkVerificationDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.AppUpdateLocalDataSource
import com.markettwits.devx.tgsignin.data.datasource.AppUpdateLocalDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.AppearanceLocalDataSource
import com.markettwits.devx.tgsignin.data.datasource.AppearanceLocalDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.AuthenticationLocalDataSource
import com.markettwits.devx.tgsignin.data.datasource.AuthenticationLocalDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.BackendReadinessDataSource
import com.markettwits.devx.tgsignin.data.datasource.BackendReadinessDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.CryptoManager
import com.markettwits.devx.tgsignin.data.datasource.CryptoManagerImpl
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleaseDataSource
import com.markettwits.devx.tgsignin.data.datasource.GitHubReleaseDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.ProfileEmojiRemoteDataSource
import com.markettwits.devx.tgsignin.data.datasource.ProfileEmojiRemoteDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.TelegramAuthApiDataSource
import com.markettwits.devx.tgsignin.data.datasource.TelegramAuthApiDataSourceImpl
import com.markettwits.devx.tgsignin.data.datasource.TelegramLoginDataSource
import com.markettwits.devx.tgsignin.data.datasource.TelegramLoginDataSourceImpl
import com.markettwits.devx.tgsignin.data.repository.AppLinkVerificationRepository
import com.markettwits.devx.tgsignin.data.repository.AppLinkVerificationRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.AppUpdateRepository
import com.markettwits.devx.tgsignin.data.repository.AppUpdateRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.AppearanceRepository
import com.markettwits.devx.tgsignin.data.repository.AppearanceRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepository
import com.markettwits.devx.tgsignin.data.repository.BackendReadinessRepositoryImpl
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepository
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepositoryImpl
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.viewmodel.AppLinkVerificationViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.AppUpdateViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.AppearanceViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.BackendReadinessViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.LoginViewModel
import com.markettwits.devx.tgsignin.ui.viewmodel.ProfileViewModel
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
    single<GitHubReleaseDataSource> {
        GitHubReleaseDataSourceImpl(
            latestReleaseUrl = GITHUB_LATEST_RELEASE_API_URL,
            userAgent = "TelegramLoginAndroidDemo/${BuildConfig.VERSION_NAME} (Android)",
            ioDispatcher = get(ioDispatcherQualifier)
        )
    }
    single<AppUpdateLocalDataSource> { AppUpdateLocalDataSourceImpl(androidContext()) }
    single<AppUpdateRepository> {
        AppUpdateRepositoryImpl(
            remoteDataSource = get(),
            localDataSource = get(),
            currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        )
    }
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
    viewModel { LoginViewModel(get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { AppearanceViewModel(get()) }
    viewModel { BackendReadinessViewModel(get()) }
    viewModel { AppLinkVerificationViewModel(get()) }
    viewModel { AppUpdateViewModel(get()) }
}

private const val GITHUB_LATEST_RELEASE_API_URL =
    "https://api.github.com/repos/MarketTwits/TelegramLoginAndroidDemo/releases/latest"
