package com.openclaw.clawchat.testapi

import com.openclaw.clawchat.network.ApplicationScope
import com.openclaw.clawchat.ui.state.MainViewModel
import com.openclaw.clawchat.ui.state.SessionViewModel
import com.openclaw.clawchat.network.protocol.GatewayConnection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Debug-only Hilt module that provides TestApiServer singleton.
 * This module is only compiled into debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
object TestApiModule {

    @Provides
    @Singleton
    fun provideTestApiServer(
        mainViewModel: MainViewModel,
        sessionViewModel: SessionViewModel,
        gatewayConnection: GatewayConnection
    ): TestApiServer {
        return TestApiServer(mainViewModel, sessionViewModel, gatewayConnection)
    }
}
