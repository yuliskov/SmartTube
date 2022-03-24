package ru.yourok.openvpn

import android.content.Context
import android.os.Environment
import java.io.File

object DataCleanManager {
    // TODO: check junk
    fun cleanApplicationData(context: Context) {
        cleanCache(context)
        deleteDatabases(context)
        cleanSharedPreference(context)
        cleanFiles(context)
    }

    private fun cleanCache(context: Context) {
        deleteFilesByDirectory(context.cacheDir)
        cleanExternalCache(context)
    }

    private fun cleanExternalCache(context: Context) {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            deleteFilesByDirectory(context.externalCacheDir)
        }
    }

    private fun deleteDatabases(context: Context) {
        for (database in context.databaseList()) {
            context.deleteDatabase(database)
        }
    }

    private fun cleanSharedPreference(context: Context) {
        //deleteFilesByDirectory(File(context.filesDir?.parent + "/shared_prefs"))
        val pref = File(context.filesDir?.parent + "/shared_prefs/VPNList.xml")
        if (pref.exists() && pref.isFile)
            pref.delete()
    }

    private fun cleanFiles(context: Context) {
        //deleteFilesByDirectory(context.filesDir)
        val folder = File(context.filesDir.toString())
        val fList = folder.listFiles()
        for (i in fList!!.indices) {
            val f: File? = fList[i]
            if (f?.path?.endsWith(".vp") == true || f?.path?.contains("uuid.dat") == true) {
                f.delete()
            }
        }
    }

    private fun deleteFilesByDirectory(directory: File?) {
        directory?.let {
            if (directory.exists() && directory.isDirectory) {
                for (item in directory.listFiles() ?: emptyArray()) {
                    item.delete()
                }
            }
        }
    }

//    fun getFolderSize(file: File): Float {
//        var size = 0f
//        try {
//            val fileList = file.listFiles()
//            for (aFileList in fileList) {
//                size = if (aFileList.isDirectory) {
//                    size + getFolderSize(aFileList)
//                } else {
//                    size + aFileList.length()
//                }
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return 0f
//        }
//        return size
//    }
}