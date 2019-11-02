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

public class MenuAdapter extends BaseAdapter {
    private Context context;
    private List<MenuItem> data = new ArrayList<MenuItem>();

    public MenuAdapter(Context context) {
        this.context = context;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public MenuItem getItem(int i) {
        return data.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    public void addItem(int image, String title) {
        data.add(new MenuItem(image, title));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_menu, null);
        }
        TextView tv = (TextView) convertView.findViewById(R.id.text_item);
        tv.setText(data.get(position).getTitle());
        ImageView iv = (ImageView) convertView.findViewById(R.id.image_item);
        iv.setImageResource(data.get(position).getImage());
        View item_bg = convertView.findViewById(R.id.item_bg);
        if (data.get(position).isSelect())
            item_bg.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.select_bg));
        else
            item_bg.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.item_bg));

        return convertView;
    }

    public void clear() {
        data.clear();
    }
}
