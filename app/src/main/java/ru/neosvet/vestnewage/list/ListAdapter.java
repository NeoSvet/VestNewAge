package ru.neosvet.vestnewage.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class ListAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<ListItem> data = new ArrayList<>();

    public ListAdapter(Context context) {
        this.context = context;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setItem(List<ListItem> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    public void addItem(ListItem item) {
        data.add(item);
    }

    public void addItem(ListItem item, String des) {
        item.setDes(des);
        data.add(item);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public ListItem getItem(int i) {
        return (i >= data.size()) ? null : data.get(i);
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
        //if (convertView == null) {
        TextView tv;
        boolean isItemList = false;
        if (data.get(position).getLink().equals("#"))
            convertView = inflater.inflate(R.layout.item_title, null);
        else if (data.get(position).getDes().equals("")) {
            convertView = inflater.inflate(R.layout.item_list, null);
            isItemList = true;
        } else {
            convertView = inflater.inflate(R.layout.item_detail, null);
            tv = convertView.findViewById(R.id.des_item);
            if (data.get(position).getDes().contains("<"))
                tv.setText(android.text.Html.fromHtml(data.get(position).getDes()));
            else
                tv.setText(data.get(position).getDes());
            if (data.get(position).getLink().equals("@"))
                tv.setTextColor(ContextCompat.getColor(context, R.color.light_gray));
        }
        //}
        tv = convertView.findViewById(R.id.text_item);
        tv.setText(data.get(position).getTitle());
        if (data.get(position).getLink().equals("@") && isItemList)
            tv.setTextColor(ContextCompat.getColor(context, R.color.light_gray));
        View item_bg = convertView.findViewById(R.id.item_bg);
        if (data.get(position).isSelect())
            item_bg.setBackgroundResource(R.drawable.select_item_bg);
        else
            item_bg.setBackgroundResource(R.drawable.item_bg);
        return convertView;
    }
}

