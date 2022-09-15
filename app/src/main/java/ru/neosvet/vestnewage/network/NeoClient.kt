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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NeoClient(
    private val type: Type,
    private val handler: LoadHandlerLite? = null
) {
    enum class Type(val value: Int) {
        MAIN(1), LOADER(2), CHECK(3), SECTION(4)
    }

    companion object {
        private const val PATH = "/cache/file"

        @JvmStatic
        fun createHttpClient(): OkHttpClient {
            val client = OkHttpClient.Builder()
            client.connectTimeout(NetConst.TIMEOUT.toLong(), TimeUnit.SECONDS)
            client.readTimeout(NetConst.TIMEOUT.toLong(), TimeUnit.SECONDS)
            client.writeTimeout(NetConst.TIMEOUT.toLong(), TimeUnit.SECONDS)
            return client.build()
        }

        fun deleteTempFiles() {
            val d = Lib.getFileP("/cache")
            d.listFiles()?.forEach { f ->
                if (f.isFile && DateUnit.isLongAgo(f.lastModified()))
                    f.delete()
            }
        }
    }

    fun getStream(url: String): BufferedInputStream {
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
        val file = Lib.getFileP(PATH + type.value)
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