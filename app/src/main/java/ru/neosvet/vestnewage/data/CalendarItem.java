package ru.neosvet.vestnewage.data;

import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.utils.Const;

public class CalendarItem {
    private final int num;
    private int color;
    private final List<String> titles = new ArrayList<>();
    private final List<String> links = new ArrayList<>();
    private boolean bold = false, poem = false, epistle = false;

    public CalendarItem(int num, int id_color) {
        this.num = num;
        color = ContextCompat.getColor(App.context, id_color);
        if (num < 1)
            bold = true;
    }

    public void setBold() {
        bold = true;
    }

    public void clear() {
        if (titles.size() > 0) {
            titles.clear();
            links.clear();
            poem = false;
            epistle = false;
        }
    }

    public String getTitle(int i) {
        return titles.get(i);
    }

    public String getLink(int i) {
        return links.get(i);
    }

    public int getCount() {
        return links.size();
    }

    public void addTitle(String title) {
        titles.add(title);
    }

    public void addLink(String link) {
        if (link.contains(Const.POEMS))
            poem = true;
        else
            epistle = true;
        links.add(link);
    }

    public Drawable getBG() {
        int bg;
        if (poem && epistle)
            bg = R.drawable.cell_bg_all;
        else if (poem)
            bg = R.drawable.cell_bg_poe;
        else if (epistle)
            bg = R.drawable.cell_bg_epi;
        else
            bg = R.drawable.cell_bg_none;
        return ContextCompat.getDrawable(App.context, bg);
    }

    public boolean isBold() {
        return bold;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getNum() {
        return num;
    }

    public String getDay() {
        if (num < 1)
            return App.context.getResources().getStringArray(R.array.week_day)[num * -1];
        else
            return Integer.toString(num);
    }
}
