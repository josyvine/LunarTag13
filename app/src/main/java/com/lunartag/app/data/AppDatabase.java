package com.lunartag.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.lunartag.app.model.AuditLog;
import com.lunartag.app.model.ManualLocation;
import com.lunartag.app.model.Photo;

/**
 * The main database class for the application.
 * UPDATED: Added ManualLocation entity and bumped version to 2 for smart workplace tracking.
 */
@Database(entities = {Photo.class, AuditLog.class, ManualLocation.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract PhotoDao photoDao();
    public abstract AuditLogDao auditLogDao();
    public abstract ManualLocationDao manualLocationDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "lunartag_database")
                            // Fallback to destructive migration is kept as per original logic 
                            // but version is bumped to trigger the table creation.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}