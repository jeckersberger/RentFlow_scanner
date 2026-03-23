package com.rentflow.scanner.di

import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.MockHardwareScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HardwareModule {
    @Binds
    @Singleton
    abstract fun bindHardwareScanner(impl: MockHardwareScanner): HardwareScanner
}
