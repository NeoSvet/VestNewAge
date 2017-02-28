package ru.neosvet.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;
import ru.neosvet.utils.Lib;

public class CalendarItem {
    private Context context;
    private int num, color;
    private List<String> links = new ArrayList<String>();
    private boolean bold = false, cur, kat = false, pos = false;

    public CalendarItem(Context context, int num, int id_color) {
        this.context = context;
        this.num = num;
        cur = id_color == R.color.white;

        color = context.getResources().getColor(id_color);
        if (num < 1 || num == 4 || num == 17 || num == 26 || num == 30) {
            bold = true;
            if (id_color == R.color.white) {
                color = context.getResources().getColor(R.color.colorAccentLight);
                links.add(Lib.LINK + "Posyl-na-Edinenie");
            }
        }
    }

    public void clear() {
        if (links.size() > 0) {
            if (links.get(0).contains("/"))
                links.clear();
            else {
                while (links.size() > 1)
                    links.remove(1);
            }
            kat = false;
            pos = false;
        }
    }

    public String getLink(int i) {
        return links.get(i) + ".html";
    }

    public int getCount() {
        return links.size();
    }

    public void addLink(String link) {
        if (link.contains("poems"))
            kat = true;
        else
            pos = true;
        links.add(link);
    }

    public Drawable getBG() {
        int bg;
        if (kat && pos)
            bg = R.drawable.cell_bg_kp;
        else if (kat)
            bg = R.drawable.cell_bg_k;
        else if (pos)
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

    public boolean isCur() {
        return cur;
    }

}
