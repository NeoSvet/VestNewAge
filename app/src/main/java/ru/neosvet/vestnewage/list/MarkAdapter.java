package ru.neosvet.vestnewage.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class MarkAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<MarkItem> data = new ArrayList<>();

    public MarkAdapter(Context context) {
        this.context = context;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void insertItem(int pos, MarkItem item) {
        data.add(pos, item);
    }

    public void addItem(MarkItem item) {
        data.add(item);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public MarkItem getItem(int i) {
        return data.get(i); //data == null ? null :
    }

    public void clear() {
        data.clear();
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv;
        if (data.get(position).getDes().equals("")) {
            convertView = inflater.inflate(R.layout.item_list, null);
        } else {
            convertView = inflater.inflate(R.layout.item_detail, null);
            tv = (TextView) convertView.findViewById(R.id.des_item);
            tv.setText(data.get(position).getDes());
        }
        tv = (TextView) convertView.findViewById(R.id.text_item);
        tv.setText(data.get(position).getTitle());
        View item_bg = convertView.findViewById(R.id.item_bg);
        if (data.get(position).isSelect())
            item_bg.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.select_item_bg));
        else
            item_bg.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.item_bg));
        return convertView;
    }

    public void removeAt(int i) {
        data.remove(i);
    }
}
