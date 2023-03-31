package ru.neosvet.vestnewage.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.http.promisesBody
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
        @JvmStatic
        val isSiteCom: Boolean
            get() = if (isCom == null) getCom() else isCom!!
        private var isCom: Boolean? = null
        private const val PATH = "/cache/file"
        private const val COM_FILE = "/com"

        private fun getCom(): Boolean =
            Lib.getFile(COM_FILE).exists().also { isCom = it }

        fun setCom(value: Boolean) {
            isCom = value
            val f = Lib.getFile(COM_FILE)
            if (value) f.createNewFile()
            else if (f.exists()) f.delete()
        }

        @JvmStatic
        fun createHttpClient(): OkHttpClient {
            val client = OkHttpClient.Builder()
            client.connectTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS)
            client.readTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS)
            client.writeTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS)
            return client.build()
        }

        fun deleteTempFiles() {
            val d = Lib.getFileP("/cache")
            d.listFiles()?.forEach { f ->
                if (f.isFile && DateUnit.isLongAgo(f.lastModified()))
                    f.delete()
            }
        }

        fun getSite(): String {
            val s = (if (isSiteCom) NetConst.SITE_COM else NetConst.SITE).substring(8)
            return s.substring(0, s.length - 1)
        }
    }

    fun getStream(url: String): BufferedInputStream {
        val u = if (isSiteCom) url.replace(NetConst.SITE, NetConst.SITE_COM)
        else url.replace("rss/", "rss.xml")
        val response = try {
            val builderRequest = Request.Builder()
            builderRequest.url(u)
            if (u.contains(NetConst.SITE))
                builderRequest.header("Referer", NetConst.SITE)
            else if (u.contains(NetConst.SITE_COM))
                builderRequest.header("Referer", NetConst.SITE_COM)
            builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
            val client = createHttpClient()
            client.newCall(builderRequest.build()).execute()
        } catch (e: Exception) {
            e.printStackTrace()
            throw SiteNoResponse()
        }
        if (response.isSuccessful.not()) throw SiteCode(response.code)
        if (response.promisesBody().not()) throw SiteNoResponse()
        val inStream = response.body.byteStream()
        val max = response.body.contentLength().toInt()
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