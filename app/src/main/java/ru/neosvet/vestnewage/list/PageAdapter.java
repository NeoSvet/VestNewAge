package ru.neosvet.vestnewage.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import ru.neosvet.vestnewage.R;

public class PageAdapter extends RecyclerView.Adapter<PageAdapter.ViewHolder> {
    private final Context context;
    private final int max;
    private int select;
    private final View.OnTouchListener click;

    public PageAdapter(Context context, int max, int select, View.OnTouchListener click) {
        this.context = context;
        this.max = max;
        this.select = select;
        this.click = click;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_page, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PageAdapter.ViewHolder holder, int pos) {
        holder.num.setText(String.valueOf(pos + 1));
        if (pos == select)
            holder.bg.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.select_bg));
        else
            holder.bg.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.item_bg));
        holder.bg.setTag(pos);
        holder.bg.setOnTouchListener(click);
    }

    public void setSelect(int select) {
        this.select = select;
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return max;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View bg;
        TextView num;

        ViewHolder(View itemView) {
            super(itemView);
            bg = itemView.findViewById(R.id.cell_bg);
            num = (TextView) itemView.findViewById(R.id.cell_num);
        }
    }
}
