package com.scan.warehouse.di

import android.content.Context
import com.scan.warehouse.BuildConfig
import com.scan.warehouse.repository.DemoRepository
import com.scan.warehouse.repository.ProductRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProductRepository(@ApplicationContext context: Context): ProductRepository {
        return if (BuildConfig.FLAVOR == "demo") {
            DemoRepository(context)
        } else {
            ProductRepository(context)
        }
    }
}
