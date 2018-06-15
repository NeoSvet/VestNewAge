package ru.neosvet.vestnewage.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MarkerActivity;

public class CheckAdapter extends BaseAdapter {
    private MarkerActivity act = null;
    private LayoutInflater inflater;
    private List<CheckItem> data = new ArrayList<CheckItem>();

    public CheckAdapter(MarkerActivity act) {
        this.act = act;
        inflater = (LayoutInflater) act.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public CheckItem getItem(int i) {
        return data.get(i); //data == null ? null :
    }

    public void addItem(String title) {
        data.add(new CheckItem(title));
    }

    public void addItem(int id, String title) {
        data.add(new CheckItem(id, title));
    }

    public void insertItem(int i, int id, String title) {
        data.add(i, new CheckItem(id, title));
    }

    @Override
    public long getItemId(int i) {
        return data.get(i).getId();
    }

    public void clear() {
        data.clear();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_check, null);
        }
        CheckBox cb = (CheckBox) convertView.findViewById(R.id.check_field);
        cb.setText(data.get(position).getTitle());
        cb.setChecked(data.get(position).isCheck());
        cb.setTag(position);
        if (act.getModeList() == 1) { //page
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = (int) view.getTag();
                    boolean check = !data.get(pos).isCheck();
                    if (pos == 0) {
                        for (int i = 0; i < data.size(); i++)
                            data.get(i).setCheck(check);
                    } else {
                        if (!check)
                            data.get(0).setCheck(check);
                        data.get(pos).setCheck(check);
                    }
                    act.update();
                }
            });
        } else { //col
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = (int) view.getTag();
                    data.get(pos).setCheck(!data.get(pos).isCheck());
                }
            });
        }
        return convertView;
    }
}
