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
import ru.neosvet.vestnewage.list.ListItem;

public class HelpAdapter extends BaseAdapter {
    private final String BUTTON = "b";
    private Context context;
    private List<ListItem> data = new ArrayList<ListItem>();

    public HelpAdapter(Context context) {
        this.context = context;
    }

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
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (button)
            convertView = inflater.inflate(R.layout.item_menu, null);
        else
            convertView = inflater.inflate(R.layout.item_detail, null);
        TextView tv = (TextView) convertView.findViewById(R.id.text_item);
        tv.setText(data.get(pos).getTitle());
        if (button) {
            ImageView img = (ImageView) convertView.findViewById(R.id.image_item);
            int icon = Integer.parseInt(data.get(pos).getLink());
            img.setImageDrawable(context.getResources().getDrawable(icon));
        } else {
            tv = (TextView) convertView.findViewById(R.id.des_item);
            tv.setText(data.get(pos).getDes());
            if (data.get(pos).getCount() == 0)
                tv.setVisibility(View.GONE);
            else
                tv.setVisibility(View.VISIBLE);
        }
        return convertView;
    }
}
