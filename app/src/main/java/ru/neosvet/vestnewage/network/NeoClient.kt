package ru.neosvet.vestnewage.network

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.MyException.SiteCode
import ru.neosvet.vestnewage.data.MyException.SiteNoResponse
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object NeoClient {
    @JvmStatic
    val isMainSite: Boolean
        get() = first
    private var first = true

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
        val u = if (!first && url.contains(NetConst.SITE))
            url.replace(NetConst.SITE, NetConst.SITE2)
        else url
        val response = try {
            val builderRequest = Request.Builder()
            builderRequest.url(u)
            if (u.contains(NetConst.SITE))
                builderRequest.header("Referer", NetConst.SITE)
            builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
            val client = createHttpClient()
            client.newCall(builderRequest.build()).execute()
        } catch (e: Exception) {
            e.printStackTrace()
            if (u.contains(NetConst.SITE)) {
                first = false;
                return getStream(u.replace(NetConst.SITE, NetConst.SITE2));
            } else throw SiteNoResponse()
        }
        if (response.code != 200) {
            if (u.contains(NetConst.SITE)) {
                first = false;
                return getStream(u.replace(NetConst.SITE, NetConst.SITE2));
            } else throw SiteCode(response.code)
        }
        if (response.body == null) throw SiteNoResponse()
        val inStream = response.body!!.byteStream()
        val max = response.body!!.contentLength().toInt()
        val file = Lib.getFileP("/cache/file")
        if (file.exists()) file.delete()
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
}