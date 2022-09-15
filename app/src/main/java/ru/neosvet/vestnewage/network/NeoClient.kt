package ru.neosvet.vestnewage.network

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.NeoException.SiteCode
import ru.neosvet.vestnewage.data.NeoException.SiteNoResponse
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object NeoClient {
    private const val PATH = "/cache/file"

    @JvmStatic
    fun createHttpClient(): OkHttpClient {
        val client = OkHttpClient.Builder()
        client.connectTimeout(NetConst.TIMEOUT.toLong(), TimeUnit.SECONDS)
        client.readTimeout(NetConst.TIMEOUT.toLong(), TimeUnit.SECONDS)
        client.writeTimeout(NetConst.TIMEOUT.toLong(), TimeUnit.SECONDS)
        return client.build()
    }

    @JvmStatic
    fun getStream(url: String, handler: LoadHandlerLite? = null): BufferedInputStream {
        val response = try {
            val builderRequest = Request.Builder()
            builderRequest.url(url)
            if (url.contains(NetConst.SITE))
                builderRequest.header("Referer", NetConst.SITE)
            builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
            val client = createHttpClient()
            client.newCall(builderRequest.build()).execute()
        } catch (e: Exception) {
            e.printStackTrace()
            throw SiteNoResponse()
        }
        if (response.code != 200) throw SiteCode(response.code)
        if (response.body == null) throw SiteNoResponse()
        val inStream = response.body!!.byteStream()
        val max = response.body!!.contentLength().toInt()
        val file = getTempFile()
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

    private fun getTempFile(): File {
        var n = 1
        var f = Lib.getFileP(PATH + n)
        while (f.exists()) {
            if (DateUnit.isLongAgo(f.lastModified()))
                f.delete()
            else {
                n++
                f = Lib.getFileP(PATH + n)
            }
        }
        return f
    }

    fun deleteTempFiles() {
        var n = 1
        var f = Lib.getFileP(PATH + n)
        while (f.exists()) {
            f.delete()
            n++
            f = Lib.getFileP(PATH + n)
        }
    }
}