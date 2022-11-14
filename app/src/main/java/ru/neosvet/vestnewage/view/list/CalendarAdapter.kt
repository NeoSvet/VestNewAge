package ru.neosvet.vestnewage.view.list;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.CalendarItem;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
    private List<CalendarItem> data = null;
    private final Clicker click;

    public CalendarAdapter(Clicker click) {
        this.click = click;
    }

    public interface Clicker {
        void onClick(View view, CalendarItem item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(CalendarAdapter.ViewHolder holder, int pos) {
        holder.setItem(data.get(pos));
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<CalendarItem> items) {
        data = items;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView num;

        ViewHolder(View itemView) {
            super(itemView);
            num = itemView.findViewById(R.id.cell_num);
        }

        @SuppressLint("ClickableViewAccessibility")
        public void setItem(CalendarItem item) {
            num.setText(item.getDay());
            num.setTextColor(item.getColor());
            itemView.setBackground(item.getBG());
            if (item.isBold())
                num.setTypeface(null, Typeface.BOLD);
            else
                num.setTypeface(null, Typeface.NORMAL);
            //itemView.setOnClickListener(view -> click.onClick(view, item));
            itemView.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP)
                    click.onClick(view, item);
                return false;
            });
        }
    }
}
