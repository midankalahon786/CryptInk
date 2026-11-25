package com.cryptink.app.di

import android.content.Context
import androidx.room.Room
import com.cryptink.app.data.local.CryptInkDatabase
import com.cryptink.app.data.local.JournalDao
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
    fun provideCryptInkDatabase(
        @ApplicationContext context: Context
    ): CryptInkDatabase {
        return Room.databaseBuilder(
            context,
            CryptInkDatabase::class.java,
            "cryptink.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideJournalDao(database: CryptInkDatabase): JournalDao {
        return database.journalDao()
    }
}