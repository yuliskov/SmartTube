package com.liskovsoft.smartyoutubetv2.common.openvpn

import android.net.Uri
import java.io.File

object Download {
    suspend fun download(link: String, file: File, onProgress: (suspend (prc: Int) -> Boolean)?): Boolean {
        if (file.exists())
            file.delete()
        var isEnd = true
        val conn = Http(Uri.parse(link))
        conn.connect()
        conn.getInputStream().use { input ->
            file.outputStream().use { fileOut ->
                val contentLength = conn.getSize()
                if (onProgress == null)
                    input?.copyTo(fileOut)
                else {
                    val buffer = ByteArray(65535)
                    val length = contentLength + 1
                    var offset: Long = 0
                    while (true) {
                        val readed = input?.read(buffer) ?: 0
                        offset += readed
                        val prc = (offset * 100 / length).toInt()
                        if (!onProgress(prc)) {
                            isEnd = false
                            break
                        }
                        if (readed <= 0)
                            break
                        fileOut.write(buffer, 0, readed)
                    }
                    fileOut.flush()
                }
                fileOut.flush()
                fileOut.close()
            }
        }
        conn.close()
        return isEnd
    }
}