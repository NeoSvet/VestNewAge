package ru.neosvet.vestnewage.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.internal.http.promisesBody
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NeoClient(
    private val handler: LoadHandlerLite? = null
) {

    companion object {
        private const val PATH = "/cache/file"

        @JvmStatic
        fun createHttpClient(oldHttp: Boolean = false): OkHttpClient =
            if (App.unsafeClient)
                UnsafeClient.createHttpClient(oldHttp)
            else {
                val client = OkHttpClient.Builder()
                if (oldHttp) client.protocols(listOf(Protocol.HTTP_1_1))
                client.connectTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS)
                client.readTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS)
                client.writeTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS)
                client.build()
            }

        fun deleteTempFiles() {
            synchronized(Unit) {
                val d = Files.parent("/cache")
                d.listFiles()?.forEach { f ->
                    if (f.isFile) f.delete()
                }
            }
        }
    }

    private fun urlsUpdate(url: String) {
        if (url.contains("urls")) return
        Urls.update(NeoClient())
    }

    fun getStream(url: String): BufferedInputStream {
        //println("stream for ${url.substring(8)}")
        val response = try {
            val builderRequest = Request.Builder()
            builderRequest.url(url)
            val host = Urls.Host
            if (url.contains(host))
                builderRequest.header(NetConst.REFERER, host)
            builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
            val client = createHttpClient()
            client.newCall(builderRequest.build()).execute()
        } catch (e: Exception) {
            //e.printStackTrace()
            urlsUpdate(url)
            throw NeoException.SiteUnavailable()
        }
        if (response.isSuccessful.not()) {
            urlsUpdate(url)
            throw NeoException.SiteCode(response.code)
        }
        if (response.promisesBody().not()) {
            urlsUpdate(url)
            throw NeoException.SiteNoResponse()
        }
        val inStream = response.body.byteStream()
        val max = response.body.contentLength().toInt()
        val file = createFile()
        val outStream = FileOutputStream(file)
        val buffer = ByteArray(1024)
        var length = inStream.read(buffer)
        var cur = 0
        while (length > 0) {
            outStream.write(buffer, 0, length)
            if (max > 0 && handler != null) {
                cur += length
                handler.postPercent(cur.percent(max))
            }
            length = inStream.read(buffer)
        }
        inStream.close()
        outStream.close()
        return BufferedInputStream(FileInputStream(file))
    }

    private fun isLongAgo(time: Long): Boolean {
        return System.currentTimeMillis() - time > 300000 // 5 min
    }

    private fun createFile(): File {
        synchronized(Unit) {
            var i = 1
            var file: File
            while (true) {
                file = Files.parent(PATH + i.toString())
                if (!file.exists()) return file
                if (file.exists() && isLongAgo(file.lastModified())) {
                    file.delete()
                    return file
                }
                i++
            }
        }
    }
}