package com.example.protegotinyever.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.protegotinyever.adapt.MessageEntity;

@Database(entities = {MessageEntity.class}, version = 1, exportSchema = false)
public abstract class ChatDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "chat_db";
    private static ChatDatabase instance;
    private int rea = 1;

    public abstract MessageDao messageDao();

    public static synchronized ChatDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                context.getApplicationContext(),
                ChatDatabase.class,
                DATABASE_NAME
            ).build();
        }
        return instance;
    }
}
