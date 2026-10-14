package com.hackqing.scale.di

import android.content.Context
import com.hackqing.scale.data.local.HackqingDatabase
import com.hackqing.scale.data.local.MeasurementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HackqingDatabase =
        HackqingDatabase.build(context)

    @Provides
    fun measurementDao(db: HackqingDatabase): MeasurementDao = db.measurementDao()
}
