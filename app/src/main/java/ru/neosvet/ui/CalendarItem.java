package ru.neosvet.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class CalendarItem {
    private Context context;
    private int num, color;
    private List<String> titles = new ArrayList<String>();
    private List<String> links = new ArrayList<String>();
    private boolean bold = false, katren = false, poslanie = false;

    public CalendarItem(Context context, int num, int id_color) {
        this.context = context;
        this.num = num;

        color = context.getResources().getColor(id_color);
        if (num < 1)
            bold = true;
    }

    public void setProm() {
        bold = true;
        color = context.getResources().getColor(R.color.colorAccentLight);
        links.add("Posyl-na-Edinenie.html");
        titles.add(context.getResources().getString(R.string.prom_for_soul_unite));
    }

    public void clear(boolean onlyTitle) {
        if (titles.size() > 0) {
            if (!bold) {
                titles.clear();
                if (!onlyTitle)
                    links.clear();
            } else {
                while (titles.size() > 1) {
                    titles.remove(1);
                    if (!onlyTitle)
                        links.remove(1);
                }
            }
            if (!onlyTitle) {
                katren = false;
                poslanie = false;
            }
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
        if (link.contains("poems"))
            katren = true;
        else
            poslanie = true;
        links.add(link);
    }

    public Drawable getBG() {
        int bg;
        if (katren && poslanie)
            bg = R.drawable.cell_bg_kp;
        else if (katren)
            bg = R.drawable.cell_bg_k;
        else if (poslanie)
            bg = R.drawable.cell_bg_p;
        else
            bg = R.drawable.cell_bg_n;
        return context.getResources().getDrawable(bg);
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
            return context.getResources().getStringArray(R.array.week_day)[num * -1];
        else
            return Integer.toString(num);
    }
}
