package ru.neosvet.ui;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;

public class ListItem implements Serializable {
    private String title, des = null;
    private List<String> heads = new ArrayList<String>();
    private List<String> links = new ArrayList<String>();
    private boolean boolSelect = false;

    public ListItem(String title, String link) {
        this.title = title;
        addLink(link);
    }

    public ListItem(String title) {
        this.title = title;
    }

    public ListItem(String title, boolean boolTitle) {
        this.title = title;
        if (boolTitle)
            this.isTitle();
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

    public void isTitle() {
        links.add("#");
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
        return boolSelect;
    }
}
