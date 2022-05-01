package ru.neosvet.vestnewage.list;

import android.util.Pair;

import java.io.Serializable;
import java.util.Iterator;

import ru.neosvet.utils.NeoList;

public class ListItem implements Serializable {
    private String title, des = null;
    private final NeoList<String> heads = new NeoList<>();
    private final NeoList<String> links = new NeoList<>();
    private boolean select = false, few = false;

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
            addLink("#");
    }

    public boolean hasLink() {
        return links.isNotEmpty();
    }

    public boolean hasFewLinks() {
        return few;
    }

    public void clear() {
        heads.clear();
        links.clear();
        few = false;
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

    public String getLink() {
        if (links.isNotEmpty())
            return links.first();
        else
            return "";
    }

    public void addLink(String head, String link) {
        heads.add(head);
        addLink(link);
    }

    public void addLink(String link) {
        if (!few)
            few = links.isNotEmpty();
        links.add(link);
    }

    public void addHead(String head) {
        heads.add(head);
    }

    public String getHead() {
        if (heads.isNotEmpty())
            return heads.first();
        else
            return "";
    }

    public boolean isSelect() {
        return select;
    }

    public Iterator<String> getLinks() {
        links.reset();
        return links;
    }

    public Iterator<Pair<String, String>> headsAndLinks() {
        heads.reset();
        links.reset();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return heads.hasNext() && links.hasNext();
            }

            @Override
            public Pair<String, String> next() {
                return new Pair<>(heads.next(), links.next());
            }
        };
    }
}
