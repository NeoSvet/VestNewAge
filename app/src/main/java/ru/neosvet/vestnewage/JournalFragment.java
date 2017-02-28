package ru.neosvet.vestnewage;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class JournalFragment extends Fragment {
    private DataBase dbJournal;
    private ListAdapter adJournal;
    private MainActivity act;
    private View container;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.fragment_journal, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.journal));
        initViews();
        return this.container;
    }

    private void createList() {
        SQLiteDatabase db = dbJournal.getWritableDatabase();
//        Cursor cursor = db.query(DataBase.NAME, null,
//                DataBase.LINK + " = ?",
//                new String[]{link}, null, null, null);
        Cursor cursor = db.query(DataBase.NAME, null, null, null, null, null, DataBase.TIME + " DESC");
        if (cursor.moveToFirst()) {
            int iTime = cursor.getColumnIndex(DataBase.TIME);
            int iLink = cursor.getColumnIndex(DataBase.LINK);
            int iTitle = cursor.getColumnIndex(DataBase.TITLE);
            int i = 0;
            ListItem item;
            DateFormat df = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
            long now = System.currentTimeMillis();
            long t;
            do {
                item = new ListItem(cursor.getString(iTitle), cursor.getString(iLink));
                t = cursor.getLong(iTime);
                item.setDes(act.lib.getDiffDate(now, t) + "\n(" + df.format(new Date(t)) + ")");
                adJournal.addItem(item);
                i++;
            } while (cursor.moveToNext() && i < 30);
            if (cursor.moveToNext()) {
                //далее
            }
        }
        db.close();
        adJournal.notifyDataSetChanged();
    }

    private void initViews() {
        container.findViewById(R.id.fabClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setVisibility(View.GONE);
                SQLiteDatabase db = dbJournal.getWritableDatabase();
                db.delete(DataBase.NAME, null, null);
                dbJournal.close();
                adJournal.clear();
                adJournal.notifyDataSetChanged();
            }
        });
        dbJournal = new DataBase(act);
        ListView lvJournal = (ListView) container.findViewById(R.id.lvJournal);
        adJournal = new ListAdapter(act);
        lvJournal.setAdapter(adJournal);
        lvJournal.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String link = adJournal.getItem(pos).getLink();
                boolean b = false;
                if (link.indexOf(BrowserActivity.ARTICLE) == Lib.LINK.length()) {
                    link = link.substring(BrowserActivity.ARTICLE.length());
                    b = true;
                }
                BrowserActivity.openActivity(act, link, b);
                adJournal.clear();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adJournal.getCount() == 0)
            createList();
    }
}
