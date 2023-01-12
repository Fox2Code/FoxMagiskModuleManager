package com.fox2code.mmm.utils.db;

import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.room.RoomDatabase;

import java.math.BigInteger;


@SuppressWarnings("unused")
public abstract class ModuleCache extends RoomDatabase {
    // table name
    public static final String TABLE_NAME = "ModuleCache";
    @PrimaryKey
    public String id;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "description")
    public String description;
    // next up is installed version (null if not installed), remote version (null if not found), and repo id (local if installed)
    @ColumnInfo(name = "installed_version")
    public BigInteger installedVersion;
    @ColumnInfo(name = "remote_version")
    public BigInteger remoteVersion;
    @ColumnInfo(name = "repo_id")
    public String repoId;

    // db structure is: internal name, pretty name, repo url, enabled
    // create the database
    public abstract <ModuleDao> ModuleDao moduleDao();

    // returns the instance
    public static ModuleCache getInstance() {
        return null;
    }
}
