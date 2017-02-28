package ru.neosvet.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class HelpAdapter extends BaseAdapter {
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
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_detail, null);
        }
        TextView tv = (TextView) convertView.findViewById(R.id.text_item);
        tv.setText(data.get(pos).getTitle());
        tv = (TextView) convertView.findViewById(R.id.des_item);
        tv.setText(data.get(pos).getDes());
        if (data.get(pos).getCount() == 0)
            tv.setVisibility(View.GONE);
        else
            tv.setVisibility(View.VISIBLE);
        return convertView;
    }
}
