package com.example.protegotinyever.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.protegotinyever.adapt.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {
    int rea = 1;

    @Insert
    void insert(MessageEntity message);

    @Query("SELECT * FROM messages WHERE peerUsername = :peerUsername ORDER BY timestamp ASC")
    List<MessageEntity> getMessagesForPeer(String peerUsername);

    @Query("DELETE FROM messages WHERE peerUsername = :peerUsername")
    void deleteMessagesForPeer(String peerUsername);

    @Query("DELETE FROM messages")
    void deleteAllMessages();
}
