package ru.neosvet.vestnewage.list;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ru.neosvet.vestnewage.R;

public class PageAdapter extends RecyclerView.Adapter<PageAdapter.ViewHolder> {
    private Context context;
    private int max, select;

    public PageAdapter(Context context, int max, int select) {
        this.context = context;
        this.max = max;
        this.select = select;
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
    }

    public void setSelect(int select) {
        this.select = select;
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return max;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View bg;
        TextView num;

        ViewHolder(View itemView) {
            super(itemView);
            bg = itemView.findViewById(R.id.cell_bg);
            num = (TextView) itemView.findViewById(R.id.cell_num);
        }
    }
}
