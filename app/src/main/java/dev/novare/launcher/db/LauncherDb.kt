package dev.novare.launcher.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local store for the workspace/folder item graph.
 *
 * The `favorites` DDL is the schema recovered from the installed Nova 8.0.18
 * artifact (CREATE TABLE string embedded in classes.dex); the column set,
 * including Nova's `flingUpIntent`, `flingDownIntent`, `zOrder` and `novaFlags`
 * additions, is reproduced verbatim so a layout can be mapped onto it without
 * a translation layer.
 */
class LauncherDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE favorites (" +
                "_id INTEGER PRIMARY KEY," +
                "title TEXT," +
                "intent TEXT," +
                "container INTEGER," +
                "screen INTEGER," +
                "cellX REAL," +
                "cellY REAL," +
                "spanX REAL," +
                "spanY REAL," +
                "itemType INTEGER," +
                "appWidgetId INTEGER NOT NULL DEFAULT -1," +
                "iconPackage TEXT," +
                "iconResource TEXT," +
                "icon BLOB," +
                "appWidgetProvider TEXT," +
                "modified INTEGER NOT NULL DEFAULT 0," +
                "restored INTEGER NOT NULL DEFAULT 0," +
                "profileId INTEGER DEFAULT -1," +
                "rank INTEGER NOT NULL DEFAULT 0," +
                "options INTEGER NOT NULL DEFAULT 0," +
                "appWidgetSource INTEGER NOT NULL DEFAULT -1," +
                "customIconSource TEXT," +
                "customIconLoadedState TEXT," +
                "flingUpIntent TEXT," +
                "flingDownIntent TEXT," +
                "zOrder INTEGER DEFAULT 0," +
                "novaFlags INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE drawer_groups (" +
                "_id INTEGER PRIMARY KEY," +
                "name TEXT," +
                "orderIndex INTEGER NOT NULL DEFAULT 0," +
                "tabColor INTEGER NOT NULL DEFAULT 0," +
                "flags INTEGER NOT NULL DEFAULT 0," +
                "modified INTEGER NOT NULL DEFAULT 0," +
                "customIconSource TEXT," +
                "customIconLoadedState TEXT)"
        )
        db.execSQL(
            "CREATE TABLE appgroups (" +
                "_id INTEGER PRIMARY KEY," +
                "groupId INTEGER," +
                "component TEXT," +
                "modified INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE customIcons (" +
                "_id INTEGER PRIMARY KEY," +
                "component TEXT," +
                "iconPackage TEXT," +
                "iconResource TEXT," +
                "flags INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX index_favorites_container ON favorites(container, screen, rank)")
        db.execSQL("CREATE UNIQUE INDEX index_customIcons_component ON customIcons(component)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val DB_NAME = "nova.db"
        const val DB_VERSION = 1
    }
}