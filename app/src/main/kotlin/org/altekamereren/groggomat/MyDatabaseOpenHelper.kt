package org.altekamereren.groggomat

import android.app.Fragment
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.jetbrains.anko.*
import org.jetbrains.anko.db.*
import java.util.*

class MyDatabaseOpenHelper(ctx: Context) : ManagedSQLiteOpenHelper(ctx, "MyDatabase", null, 9), AnkoLogger{

    companion object {
        private var instance: MyDatabaseOpenHelper? = null

        @Synchronized
        fun getInstance(ctx: Context): MyDatabaseOpenHelper {
            if (instance == null) {
                instance = MyDatabaseOpenHelper(ctx.getApplicationContext())
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        info("DB onCreate")
        db.createTable("Kryss", true,
                "_id" to INTEGER + PRIMARY_KEY,
                "device" to TEXT + NOT_NULL,
                "real_id" to INTEGER ,
                "type" to INTEGER + NOT_NULL,
                "count" to INTEGER + NOT_NULL,
                "time" to INTEGER + NOT_NULL,
                "kamerer" to INTEGER + NOT_NULL,
                "replaces_id" to INTEGER,
                "replaces_device" to TEXT)

        db.createTable("Kamerer", true,
                "_id" to INTEGER + PRIMARY_KEY,
                "name" to TEXT + NOT_NULL,
                "weight" to REAL,
                "male" to INTEGER + NOT_NULL,
                "updated" to INTEGER + NOT_NULL)

        /*val r = Random()
        for(i in 0..9999) {
            Kryss(i.toLong(), "test", r.nextInt(4), 1 + r.nextInt(4), System.currentTimeMillis() - r.nextInt(1000*3600*24*10), r.nextInt(25).toLong(), if(r.nextInt(30)==0 && i > 100) (i - r.nextInt(99) - 1).toLong() else null, "test")
                .insert(db)
        }*/

        var men = arrayOf(
                "Andreas Hauspurg",
                "Damir Basic Knezevic",
                "David Wahlqvist",
                "Douglas Clifford",
                "Erik Löfquist",
                "Jens Ogniewski",
                "Jesper Hasselquist",
                "Johan Ruuskanen",
                "Mattias Lilja",
                "Olof Zetterqvist",
                "Oskar Fransén",
                "Peder Andersson",
                "Per Nelsson",
                "Philip Ljungkvist",
                "Pontus Persson",
                "Sam Persson",
                "Svante Rosenlind",
                "Viktor Hjertenstein",
                "Övrig man 1",
                "Övrig man 2")

        var women = arrayOf(
                "Carolina Svensson",
                "Elin Svensson",
                "Elisabet Benson",
                "Ingeborg Hjorth",
                "Jules Hanley",
                "Övrig kvinna 1",
                "Övrig kvinna 2")

        for(i in men.indices) {
            db.insert("Kamerer", "_id" to i, "name" to men[i], "male" to 1, "updated" to 1/*, "weight" to r.nextDouble()*60 + 50*/)
        }
        for(i in women.indices) {
            db.insert("Kamerer", "_id" to i+men.size, "name" to women[i], "male" to 0, "updated" to 1/*, "weight" to r.nextDouble()*40 + 40*/)
        }

        /*db.execSQL("create index kryss_replaces on Kryss(replaces_id, replaces_device) where replaces_id is not null or replaces_device is not null")
        db.execSQL("create index real_id on Kryss(real_id, device) where real_id is not null")
        db.execSQL("create index kamerer_list on Kryss(kamerer, time desc)")*/

        info("DB created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        info("DB onUpgrade")
        db.dropTable("Kryss", true)
        db.dropTable("Kamerer", true)
        onCreate(db)
    }
}

// Access properties for Context (you could use it in Activity, Service etc.)
val Context.database: MyDatabaseOpenHelper
    get() = MyDatabaseOpenHelper.getInstance(applicationContext)
// Access property for Fragment
val Fragment.database: MyDatabaseOpenHelper
    get() = MyDatabaseOpenHelper.getInstance(activity.applicationContext)

