package ru.neosvet.vestnewage.loader.page;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.NeoList;
import ru.neosvet.vestnewage.network.NeoClient;
import ru.neosvet.vestnewage.network.NetConst;
import ru.neosvet.vestnewage.utils.Lib;

public class PageParser {
    private final NeoList<HTMLElem> content = new NeoList<>();

    public void load(String url, String start) throws Exception {
        InputStream in = NeoClient.getStream(url);
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s;
        while ((s = br.readLine()) != null) {
            if (s.contains(start))
                break;
        }
        if (s == null) {
            br.close();
            in.close();
            return;
        }
        StringBuilder sb = new StringBuilder(s);
        while ((s = br.readLine()) != null) {
            if (s.contains("<!--/row-->"))
                break;
            sb.append(" ").append(s);
        }
        br.close();
        in.close();

        String t = sb.toString();
        t = t.replace("&nbsp;", " ")
                .replace("<br> ", "<br>")
                .replace("<span> </span>", " ")
                .replace("b>", "strong>")
                .replace("</strong> <strong>", " ")
                .replace("</span>", "")
                .replace("</div>", "");

        String[] m = t.split("<");
        int n;
        HTMLElem elem;
        boolean wasNoind = false, startPar = false;

        for (int i = 0; i < m.length; i++) {
            s = m[i].trim();
            if (s.contains("!--")) {
                n = s.indexOf("!--");
                if (n == 1) n = 0;
                s = s.substring(0, n) + s.substring(s.indexOf(">", n) + 1);
            }
            if (s.length() == 0) continue;
            if (s.indexOf(Const.DIV) == 0 || s.indexOf(Const.SPAN) == 0) {
                s = s.substring(s.indexOf(">") + 1);
                if (s.length() > 0) {
                    elem = new HTMLElem(Const.TEXT);
                    elem.setHtml(s);
                    content.add(elem);
                }
                continue;
            }

            elem = new HTMLElem();
            n = s.indexOf(" ");
            if (s.indexOf(">") < n || n == -1)
                n = s.indexOf(">");
            if (n == -1)
                n = s.length();
            if (s.indexOf("/") == 0) { //end tag
                elem.tag = s.substring(1, n);
                if (elem.tag.equals(Const.PAR))
                    startPar = false;
                if (content.isNotEmpty()) {
                    if (content.current().tag.equals(elem.tag)) {
                        content.current().end = true;
                        if (s.indexOf(">") < s.length() - 1) {
                            elem = new HTMLElem(Const.TEXT);
                            elem.setHtml(m[i].substring(m[i].indexOf(">") + 1));
                            content.add(elem);
                        }
                        continue;
                    }
                    elem.start = false;
                    elem.end = true;
                }
                content.add(elem);
                continue;
            }
            elem.start = true;
            elem.end = false;
            elem.tag = s.substring(0, n);
            elem.setHtml(m[i].substring(m[i].indexOf(">") + 1));
            if (elem.tag.equals("em")) {
                content.current().setHtml(content.current().html + elem.html);
                continue;
            }
            if (elem.tag.equals(Const.PAR)) {
                if (startPar)
                    content.add(new HTMLElem(Const.PAR));
                else
                    startPar = true;
            }
            if (elem.tag.equals(Const.LINK)) {
                if (s.contains("data-ajax-url")) {
                    i++;
                    continue;
                }
                n = s.indexOf("href") + 6;
                if (n == 5) continue;
                if (s.contains("\""))
                    elem.par = s.substring(n, s.indexOf("\"", n));
                else
                    elem.par = s.substring(n, s.indexOf("'", n));
                elem.par = elem.par.replace("..", "").replace("&#x2B;", "+");
                if (elem.par.contains(".jpg") && elem.par.indexOf("/") == 0)
                    elem.par = NetConst.SITE + elem.par.substring(1);
            }
            if (elem.tag.equals(Const.IMAGE)) {
                elem.par = s.substring(n).replace("=\"/", "=\"http://blagayavest.info/");
            }
            if (elem.tag.equals(Const.FRAME)) {
                elem.tag = Const.LINK;
                n = s.indexOf("src") + 5;
                elem.par = s.substring(n, s.indexOf("\"", n));
                elem.setHtml(App.context.getString(R.string.video_on_site));
            }
            if (elem.tag.equals(Const.PAR) || elem.tag.indexOf(Const.HEAD) == 0) {
                s = s.substring(0, s.indexOf(">")).replace("\"", "'");
                if (s.contains(Const.CLASS)) {
                    n = s.indexOf(Const.CLASS);
                    elem.par = s.substring(n, s.indexOf("'", n + Const.CLASS.length() + 2) + 1);
                    if (elem.par.contains("poem") && url.contains("poem"))
                        elem.par = "";
                    else if (elem.par.contains("noind")) {
                        if (wasNoind) {
                            content.current().tag = Const.LINE;
                            content.current().html = elem.html;
                            wasNoind = false;
                            continue;
                        } else
                            wasNoind = true;
                    }
                }
                if (s.contains(Const.STYLE)) {
                    n = s.indexOf(Const.STYLE);
                    s = s.substring(n, s.indexOf("'", n + Const.STYLE.length() + 2)) + ";'";
                    s = s.replace(";;", ";");
                    if (s.contains(Const.TEXTCOLOR)) {
                        n = s.indexOf(Const.TEXTCOLOR);
                        s = s.substring(0, n) + s.substring(s.indexOf(";", n) + 1);
                    }
                    if (s.contains(Const.COLOR)) {
                        n = s.indexOf(Const.COLOR);
                        s = s.substring(0, n) + s.substring(s.indexOf(";", n) + 1);
                    }
                    if (s.length() > 3) {
                        if (elem.par.length() == 0)
                            elem.par = s;
                        else
                            elem.par += " " + s;
                    }
                }
            }
            content.add(elem);
        }
        content.reset(false);
    }

    public void clear() {
        content.clear();
    }

    public String getCurrentElem() {
        return itemToString();
    }

    public String getNextElem() {
        if (!content.hasNext())
            return null;
        content.next();
        return itemToString();
    }

    private String itemToString() {
        HTMLElem elem = content.current();
        if (elem.end)
            return elem.getCode();
        StringBuilder s = new StringBuilder(elem.getCode());
        int end = 1;
        while (end > 0 && content.hasNext()) {
            HTMLElem e = content.next();
            s.append(e.getCode());
            if (elem.tag.equals(e.tag)) {
                if (e.start) end++;
                if (e.end) end--;
            }
        }
        while (end > 1) {
            s.append("</").append(elem.tag).append(">");
            end--;
        }
        return s.toString();
    }

    public HTMLElem curItem() {
        return content.current();
    }

    public String getNextItem() {
        if (!content.hasNext())
            return null;
        return content.next().getCode();
    }

    public String getLink() {
        if (content.current().tag.equals(Const.LINK))
            return content.current().par;
        else
            return null;
    }

    public String getText() {
        return Lib.withOutTags(content.current().html);
    }

    public boolean isHead() {
        return content.current().tag.indexOf(Const.HEAD) == 0;
    }
}