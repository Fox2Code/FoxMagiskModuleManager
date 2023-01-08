package com.fox2code.mmm.utils.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.RoomDatabase;


@SuppressWarnings("unused")
@Entity(tableName = "repos")
public abstract class RepoList extends RoomDatabase {
    public static final String TABLE_NAME = "RepoList";
    @PrimaryKey
    public String id;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "url")
    public String url;
    @ColumnInfo(name = "enabled")
    public boolean enabled;

    // db structure is: internal name, pretty name, repo url, enabled
    // create the database
    // dao object
    public abstract <RepoDao> RepoDao repoDao();
}
