package ru.neosvet.vestnewage.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class HelpAdapter extends BaseAdapter {
    private final String BUTTON = "b";
    private final List<ListItem> data = new ArrayList<>();

    public void clear() {
        data.clear();
    }

    public void addItem(String title, String des) {
        ListItem item = new ListItem(title);
        item.setDes(des);
        data.add(item);
    }

    public void insertItem(int index, String title, int icon) {
        ListItem item = new ListItem(title);
        if (icon != 0) {
            item.setDes(BUTTON);
            item.addLink(Integer.toString(icon));
        }
        data.add(index, item);
    }

    public void removeItem(int index) {
        if (data.size() > index)
            data.remove(index);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public ListItem getItem(int i) {
        return data.get(i); //data == null ? null :
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        boolean button = data.get(pos).getDes().equals(BUTTON);
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (button)
            convertView = inflater.inflate(R.layout.item_menu, null);
        else
            convertView = inflater.inflate(R.layout.item_detail, null);
        TextView tv = convertView.findViewById(R.id.text_item);
        tv.setText(data.get(pos).getTitle());
        View item_bg = convertView.findViewById(R.id.item_bg);
        if (button) {
            item_bg.setBackgroundResource(R.drawable.card_bg);
            ImageView img =  convertView.findViewById(R.id.image_item);
            int icon = Integer.parseInt(data.get(pos).getLink());
            img.setImageResource(icon);
        } else {
            tv = convertView.findViewById(R.id.des_item);
            tv.setText(data.get(pos).getDes());
            if (data.get(pos).getCount() == 0) {
                item_bg.setBackgroundResource(R.drawable.card_bg);
                tv.setVisibility(View.GONE);
            } else {
                item_bg.setBackgroundResource(R.drawable.item_bg);
                tv.setVisibility(View.VISIBLE);
            }
        }
        return convertView;
    }
}
