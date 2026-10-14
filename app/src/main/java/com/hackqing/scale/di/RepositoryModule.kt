package com.hackqing.scale.di

import android.content.Context
import com.hackqing.scale.ble.ScaleGattClient
import com.hackqing.scale.data.local.ProfileStore
import com.hackqing.scale.health.HealthConnectManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun profileStore(@ApplicationContext context: Context): ProfileStore = ProfileStore(context)

    @Provides
    @Singleton
    fun scaleGattClient(@ApplicationContext context: Context): ScaleGattClient =
        ScaleGattClient(context)

    @Provides
    @Singleton
    fun healthConnectManager(@ApplicationContext context: Context): HealthConnectManager =
        HealthConnectManager(context)
}
