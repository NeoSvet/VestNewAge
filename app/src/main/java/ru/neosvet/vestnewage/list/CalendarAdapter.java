package ru.neosvet.vestnewage.list;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
    private final List<CalendarItem> data = new ArrayList<>();
    private final View.OnTouchListener click;

    public CalendarAdapter(View.OnTouchListener click) {
        this.click = click;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar, parent, false);
        return new ViewHolder(view);
    }

    public CalendarItem getItem(int i) {
        return data.get(i);
    }

    @Override
    public void onBindViewHolder(CalendarAdapter.ViewHolder holder, int pos) {
        holder.num.setText(data.get(pos).getDay());
        holder.num.setTextColor(data.get(pos).getColor());
        holder.bg.setBackground(data.get(pos).getBG());
        if (data.get(pos).isBold()) {
            holder.num.setTypeface(null, Typeface.BOLD);
        } else {
            holder.num.setTypeface(null, Typeface.NORMAL);
        }
        holder.bg.setTag(pos);
        holder.bg.setOnTouchListener(click);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void clear() {
        data.clear();
    }

    public void addItem(CalendarItem calendarItem) {
        data.add(calendarItem);
    }

    public int indexOf(int d) {
        boolean begin = false;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getNum() == 1) {
                if (begin) return -1;
                begin = true;
            }
            if (begin && data.get(i).getNum() == d) {
                return i;
            }
        }
        return -1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View bg;
        TextView num;

        ViewHolder(View itemView) {
            super(itemView);
            bg = itemView.findViewById(R.id.cell_bg);
            num = itemView.findViewById(R.id.cell_num);
        }
    }
}
