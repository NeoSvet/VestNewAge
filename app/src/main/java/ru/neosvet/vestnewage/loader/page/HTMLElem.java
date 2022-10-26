package ru.neosvet.vestnewage.loader.page;

public class HTMLElem {
    public String tag, par = "";
    private String html = "";
    public boolean start = false, end = false;

    public HTMLElem() {
    }

    public HTMLElem(String tag) {
        this.tag = tag;
        start = !tag.equals(Const.PAR);
        end = true;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        if (html.contains("&")) //"&#x"
            this.html = android.text.Html.fromHtml(html).toString();
        else if (tag.equals(Const.LINK))
            this.html = html.trim();
        else
            this.html = html;
    }

    public String getCode() {
        if (tag.equals(Const.LINE))
            return "<" + tag + ">" + html;
        if (!start && end)
            return "</" + tag + ">";
        if (tag.equals(Const.TEXT))
            return html;
        if (tag.equals(Const.IMAGE))
            return "<" + tag + " " + par;
        String s;
        if (par.length() == 0)
            s = "<" + tag + ">" + html;
        else if (tag.equals(Const.LINK))
            s = "<" + tag + " href=\"" + par + "\">" + html;
        else
            s = "<" + tag + " " + par + ">" + html;
        if (end)
            return s + "</" + tag + ">";
        else
            return s;
    }
}