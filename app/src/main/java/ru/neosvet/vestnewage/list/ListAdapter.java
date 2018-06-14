package ru.neosvet.vestnewage.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class ListAdapter extends BaseAdapter {
    private Context context;
    private LayoutInflater inflater;
    private List<ListItem> data = new ArrayList<ListItem>();
    private boolean animation = false;

    public ListAdapter(Context context) {
        this.context = context;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void insertItem(int pos, ListItem item) {
        data.add(pos, item);
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
            tv = (TextView) convertView.findViewById(R.id.des_item);
            if (data.get(position).getDes().contains("<"))
                tv.setText(android.text.Html.fromHtml(data.get(position).getDes()));
            else
                tv.setText(data.get(position).getDes());
            if (data.get(position).getLink().equals("@"))
                tv.setTextColor(context.getResources().getColor(R.color.light_gray));
        }
        //}
        tv = (TextView) convertView.findViewById(R.id.text_item);
        tv.setText(data.get(position).getTitle());
        if (data.get(position).getLink().equals("@") && isItemList)
            tv.setTextColor(context.getResources().getColor(R.color.light_gray));
        if (data.get(position).isSelect())
            convertView.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.select_item_bg));
        else
            convertView.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.item_bg));
        if (animation) {
            convertView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.blink));
            animation = false;
        }
        return convertView;
    }

    public void setAnimation(boolean animation) {
        this.animation = animation;
    }
}

