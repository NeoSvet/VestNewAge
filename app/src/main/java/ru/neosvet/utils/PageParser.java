package ru.neosvet.utils;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class PageParser {
    static String HEAD = "h", LINK = "a", PAR = "p", STYLE = "style", CLASS = "class",
            TEXTCOLOR = "text-color", COLOR = "color", LINE = "br",
            IMAGE = "img", FRAME = "iframe", DIV = "div", SPAN = "span", TEXT = "text";
    Context context;
    int cur, index;
    List<HTMLElem> content = new ArrayList<HTMLElem>();

    private class HTMLElem {
        public String tag, par = "";
        private String html = "";
        public boolean start = false, end = false;

        public HTMLElem() {
        }

        public HTMLElem(String tag) {
            this.tag = tag;
            start = !tag.equals(PAR);
            end = true;
        }

        public String getHtml() {
            return html;
        }

        public void setHtml(String html) {
            if (html.contains("& ")) //"&#x"
                this.html = android.text.Html.fromHtml(html).toString();
            else
                this.html = html;
        }

        public String getCode() {
            if (tag.equals(LINE))
                return "<" + tag + ">" + html;
            if (!start && end)
                return "</" + tag + ">";
            if (tag.equals(TEXT))
                return html;
            if (tag.equals(IMAGE))
                return "<" + tag + " " + par;
            String s;
            if (par.length() == 0)
                s = "<" + tag + ">" + html;
            else if (tag.equals(LINK))
                s = "<" + tag + " href=\"" + par + "\">" + html;
            else
                s = "<" + tag + " " + par + ">" + html;
            if (end)
                return s + "</" + tag + ">";
            else
                return s;
        }
    }

    public PageParser(Context context) {
        this.context = context;
    }

    public void load(String url, String start) throws Exception {
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s;
        while ((s = br.readLine()) != null && !ProgressHelper.isCancelled()) {
            if (s.contains(start))
                break;
        }
        if (ProgressHelper.isCancelled()) {
            br.close();
            in.close();
            return;
        }
        String t = "";
        while ((s = br.readLine()) != null && !ProgressHelper.isCancelled()) {
            if (s.contains("<!--/row-->"))
                break;
            t += s;
        }
        br.close();
        in.close();
        if (ProgressHelper.isCancelled())
            return;

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
            if (s.indexOf(DIV) == 0 || s.indexOf(SPAN) == 0) {
                s = s.substring(s.indexOf(">") + 1);
                if (s.length() > 0) {
                    elem = new HTMLElem(TEXT);
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
                if (elem.tag.equals(PAR))
                    startPar = false;
                if (content.size() > 0) {
                    n = content.size() - 1;
                    if (content.get(n).tag.equals(elem.tag)) {
                        if (s.indexOf(">") < s.length() - 1) {
                            elem = new HTMLElem(TEXT);
                            elem.setHtml(m[i].substring(m[i].indexOf(">") + 1));
                            content.add(elem);
                        }
                        content.get(n).end = true;
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
            if (elem.tag.equals(PAR)) {
                if (startPar)
                    content.add(new HTMLElem(PAR));
                else
                    startPar = true;
            }
            if (elem.tag.equals(LINK)) {
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
                elem.par = elem.par.replace("..", "");
                if (elem.par.contains(".jpg") && elem.par.indexOf("/") == 0)
                    elem.par = Const.SITE + elem.par.substring(1);
            }
            if (elem.tag.equals(IMAGE)) {
                elem.par = s.substring(n).replace("=\"/", "=\"http://blagayavest.info/");
            }
            if (elem.tag.equals(FRAME)) {
                elem.tag = LINK;
                n = s.indexOf("src") + 5;
                elem.par = s.substring(n, s.indexOf("\"", n));
                elem.setHtml(context.getResources().getString(R.string.video_on_site));
            }
            if (elem.tag.equals(PAR) || elem.tag.indexOf(HEAD) == 0) {
                s = s.substring(0, s.indexOf(">")).replace("\"", "'");
                if (s.contains(CLASS)) {
                    n = s.indexOf(CLASS);
                    elem.par = s.substring(n, s.indexOf("'", n + CLASS.length() + 2) + 1);
                    if (elem.par.contains("poem") && url.contains("poem"))
                        elem.par = "";
                    else if (elem.par.contains("noind")) {
                        if (wasNoind) {
                            n = content.size() - 1;
                            content.get(n).tag = LINE;
                            content.get(n).html = elem.html;
                            wasNoind = false;
                            continue;
                        } else
                            wasNoind = true;
                    }
                }
                if (s.contains(STYLE)) {
                    n = s.indexOf(STYLE);
                    s = s.substring(n, s.indexOf("'", n + STYLE.length() + 2)) + ";'";
                    s = s.replace(";;", ";");
                    if (s.contains(TEXTCOLOR)) {
                        n = s.indexOf(TEXTCOLOR);
                        s = s.substring(0, n) + s.substring(s.indexOf(";", n) + 1);
                    }
                    if (s.contains(COLOR)) {
                        n = s.indexOf(COLOR);
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
    }

    public void clear() {
        content.clear();
    }

    public String getFirstElem() {
        index = -1;
        cur = -1;
        return getNextElem();
    }

    public String getNextElem() {
        index++;
        if (index >= content.size())
            return null;
        cur = index;
        HTMLElem elem = content.get(index);
        if (elem.end)
            return elem.getCode();
        StringBuilder s = new StringBuilder(elem.getCode());
        int end = 1;
        index++;
        while (end > 0 && index < content.size()) {
            s.append(content.get(index).getCode());
            if (elem.tag.equals(content.get(index).tag)) {
                if (content.get(index).start)
                    end++;
                if (content.get(index).end)
                    end--;
            }
            index++;
            if (index == content.size())
                break;
        }
        index--;
        while (end > 1) {
            s.append("</" + elem.tag + ">");
            end--;
        }
        return s.toString();
    }

    public String getNextItem() {
        index++;
        if (index >= content.size())
            return null;
        cur = index;
        return content.get(index).getCode();
    }

    public String getLink() {
        if (content.get(cur).tag.equals(LINK))
            return content.get(cur).par;
        else
            return null;
    }

    public String getText() {
        return Lib.withOutTags(content.get(cur).html);
    }

    public boolean isHead() {
        return content.get(cur).tag.indexOf(HEAD) == 0;
    }

}
