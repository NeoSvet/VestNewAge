package ru.neosvet.vestnewage.loader.page

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.NeoList
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.fromHTML
import java.io.BufferedReader
import java.io.InputStreamReader

class PageParser(private val client: NeoClient) {
    private val content = NeoList<HTMLElem>()
    val nextItem: String?
        get() = if (!content.hasNext()) null else content.next().code
    val link: String?
        get() = if (content.isNotEmpty && content.current().tag == Const.LINK) content.current().par else null
    val text: String
        get() = content.current().html.fromHTML
    val isHead: Boolean
        get() = if (content.isNotEmpty) content.current().tag.startsWith(Const.HEAD) else false
    val isImage: Boolean
        get() = if (content.isNotEmpty) content.current().tag.startsWith(Const.IMAGE) else false
    val isSimple: Boolean
        get() = content.current().let { it.tag == Const.TEXT || (it.start.not() && it.end) }

    fun load(url: String, startString: String) {
        var start = if (startString.isEmpty()) "page-title" else startString
        val end = "<!--/row-->"
        val stream = client.getStream(url)
        val br = BufferedReader(InputStreamReader(stream), 1000)
        if (url.contains("#") && !url.contains("#1"))
            start = "a name=\"" + url.substring(url.indexOf("#") + 1)
        var line: String? = br.readLine()
        while (line != null) {
            if (line.contains(start)) break
            line = br.readLine()
        }
        while (line != null) {
            if (line.contains("<h")) break
            line = br.readLine()
        }
        if (line == null) {
            br.close()
            stream.close()
            return
        }
        val sb = StringBuilder(line)
        line = br.readLine()
        while (line != null) {
            if (line.contains(end)) break
            sb.append(" ").append(line)
            line = br.readLine()
        }
        br.close()
        stream.close()
        if (end.contains("print2")) {
            if (sb[1] == 't') sb.delete(0, 33) //if <td else <h1
            if (line?.contains("div class=\"next") == false)
                sb.delete(sb.length - 10, sb.length)
        }
        var t = sb.toString()
        t = t.replace("&nbsp;", " ")
            .replace("<br> ", "<br>")
            .replace(" <br>", "<br>")
            .replace("<span> </span>", " ")
            .replace("b>", "strong>")
            .replace("</strong> <strong>", " ")
            .replace("em>", "i>")
            .replace("</span>", "")
            .replace("</div>", "")
        val m = t.split("<".toRegex())
        var n: Int
        var i = 0
        var elem: HTMLElem
        var wasNoind = false
        var startPar = false
        while (i < m.size) {
            var s = m[i].trim()
            if (s.isEmpty()) {
                i++
                continue
            }
            if (s[s.length - 1] != '>') s = m[i]
            if (s.contains("!--")) {
                n = s.indexOf("!--")
                if (n == 1) n = 0
                s = s.substring(0, n) + s.substring(s.indexOf(">", n) + 1)
            }
            if (s.isEmpty()) {
                i++
                continue
            }
            if (s.startsWith(Const.DIV) || s.startsWith(Const.SPAN)) {
                s = s.substring(s.indexOf(">") + 1)
                if (s.isNotEmpty())
                    content.current().html += s
                i++
                continue
            }
            elem = HTMLElem()
            n = s.indexOf(" ")
            if (s.indexOf(">") < n || n == -1)
                n = s.indexOf(">")
            if (n == -1) n = s.length
            if (s.startsWith("/")) { //end tag
                elem.tag = s.substring(1, n)
                if (elem.tag == Const.PAR) startPar = false
                if (content.isNotEmpty) {
                    when (content.current().tag) {
                        elem.tag -> content.current().end = true
                        else -> {
                            elem.start = false
                            elem.end = true
                            content.add(elem)
                        }
                    }
                }
                if (s.indexOf(">") < s.length - 1) {
                    elem = HTMLElem(Const.TEXT)
                    elem.html = s.substring(s.indexOf(">") + 1)
                    content.add(elem)
                }
                i++
                continue
            }
            elem.start = true
            elem.end = false
            elem.tag = s.substring(0, n)
            elem.html = m[i].substring(m[i].indexOf(">") + 1)
            if (elem.tag.startsWith(Const.HEAD))
                wasNoind = false
            else when (elem.tag) {
                Const.LINK -> {
                    if (s.contains("data-ajax-url")) {
                        i++
                        continue
                    }
                    n = s.indexOf("href") + 6
                    if (n == 5) {
                        i++
                        n = s.indexOf("name") + 6
                        if (n == 5) continue
                        elem.par = "name:" + //this label for PageLoader about second poem per day
                                if (s.contains("\"")) s.substring(n, s.indexOf("\"", n))
                                else s.substring(n, s.indexOf("'", n))
                        elem.end = true
                        content.add(elem)
                        continue
                    }
                    if (s.contains("\""))
                        elem.par = convertUrl(s.substring(n, s.indexOf("\"", n)))
                    else
                        elem.par = convertUrl(s.substring(n, s.indexOf("'", n)))
                    if (elem.par.contains(".jpg") && elem.par.startsWith("/"))
                        elem.par = Urls.Site + elem.par.substring(1)
                    if (elem.html.isEmpty() && !elem.par.contains(".jpg")) {
                        s = elem.par.substring(elem.par.lastIndexOf("/") + 1)
                        if (s.contains("?")) s = s.substring(0, s.indexOf("?"))
                        elem.html = s
                    }
                }

                Const.IMAGE -> {
                    elem.par = convertUrl(s.substring(n + 1))
                        .replace("=\"/", "=\"" + Urls.Site)
                    elem.end = true
                }

                Const.FRAME -> {
                    elem.tag = Const.LINK
                    n = s.indexOf("src") + 5
                    elem.par = s.substring(n, s.indexOf("\"", n))
                    elem.html = App.context.getString(R.string.video_on_site)
                }

                Const.PAR -> {
                    if (startPar) content.add(HTMLElem(Const.PAR)) else startPar = true
                    s = s.substring(0, s.indexOf(">")).replace("\"", "'")
                    if (s.contains(Const.CLASS)) {
                        n = s.indexOf(Const.CLASS)
                        s = s.substring(n, s.indexOf("'", n + Const.CLASS.length + 2) + 1)
                        if (s.contains("poem") && url.contains("poem")) {
                            elem.par = ""
                            wasNoind = false
                        } else if (s.contains("noind")) {
                            if (wasNoind) {
                                val e = content.current()
                                if (e.html.isEmpty()) {
                                    e.tag = Const.LINE
                                    e.html = elem.html
                                } else {
                                    e.end = false
                                    elem.tag = Const.LINE
                                    elem.end = true
                                    content.add(elem)
                                }
                                wasNoind = false
                                i++
                                continue
                            } else {
                                elem.par = s
                                wasNoind = true
                            }
                        } else {
                            elem.par = s
                            wasNoind = false
                        }
                    }
                    if (s.contains(Const.STYLE)) {
                        n = s.indexOf(Const.STYLE)
                        s = s.substring(n, s.indexOf("'", n + Const.STYLE.length + 2)) + ";'"
                        s = s.replace(";;", ";")
                        if (s.contains(Const.TEXTCOLOR)) {
                            n = s.indexOf(Const.TEXTCOLOR)
                            s = s.substring(0, n) + s.substring(s.indexOf(";", n) + 1)
                        }
                        if (s.contains(Const.COLOR)) {
                            n = s.indexOf(Const.COLOR)
                            s = s.substring(0, n) + s.substring(s.indexOf(";", n) + 1)
                        }
                        if (s.length > 3) {
                            if (elem.par.isEmpty()) elem.par = s else elem.par += " $s"
                        }
                    }
                }
            }
            content.add(elem)
            i++
        }
        content.reset(false)
    }

    private fun convertUrl(url: String) = url.replace("..", "").let {
        if (it.contains("&")) it.fromHTML
        else it
    }

    fun clear() {
        content.clear()
    }

    val currentElem: String
        get() = itemToString()
    val nextElem: String?
        get() {
            if (!content.hasNext()) return null
            content.next()
            return itemToString()
        }

    private fun itemToString(): String {
        if (!content.isNotEmpty) return ""
        val elem = content.current()
        if (elem.end) return elem.code
        val s = StringBuilder(elem.code)
        var end = 1
        while (end > 0 && content.hasNext()) {
            val e = content.next()
            s.append(e.code)
            if (elem.tag == e.tag) {
                if (e.start) end++
                if (e.end) end--
            }
        }
        while (end > 1) {
            s.append("</").append(elem.tag).append(">")
            end--
        }
        return s.toString()
    }
}