package ru.neosvet.vestnewage.list;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ListItem implements Serializable {
    private String title, des = null;
    private final List<String> heads = new ArrayList<>();
    private final List<String> links = new ArrayList<>();
    private boolean select = false;

    public ListItem(String title, String link) {
        this.title = title;
        addLink(link);
    }

    public ListItem(String title) {
        this.title = title;
    }

    public ListItem(String title, boolean onlyTitle) {
        this.title = title;
        if (onlyTitle)
            links.add("#");
    }

    public void clear() {
        links.clear();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDes() {
        if (des == null) return "";
        return des;
    }

    public void setDes(String des) {
        this.des = des;
    }

    public int getCount() {
        return links.size();
    }

    public String getLink() {
        if (links.size() == 0)
            return "";
        return links.get(0);
    }

    public String getLink(int index) {
        return links.get(index);
    }

    public void addLink(String head, String link) {
        heads.add(head);
        addLink(link);
    }

    public void addLink(String link) {
        links.add(link);
    }

    public void addHead(String head) {
        heads.add(head);
    }

    public String getHead(int index) {
        if (index < heads.size())
            return heads.get(index);
        else
            return "";
    }

    public boolean isSelect() {
        return select;
    }
}
