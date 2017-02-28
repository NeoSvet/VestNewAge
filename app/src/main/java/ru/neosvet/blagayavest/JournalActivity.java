package ru.neosvet.blagayavest;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.MyActivity;
import ru.neosvet.utils.DataBase;

public class JournalActivity extends MyActivity {
    private DataBase dbJournal;
    private ListAdapter adJournal;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);
        initViews();
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
                item.setDes(getDiffDate(now, t) + "\n(" + df.format(new Date(t)) + ")");
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

    private String getDiffDate(long now, long t) {
        t = (now - t) / 1000;
        int k;
        if (t < 60) {
            if (t == 0)
                t = 1;
            k = 0;
        } else {
            t = t / 60;
            if (t < 60)
                k = 3;
            else {
                t = t / 60;
                if (t < 24)
                    k = 6;
                else {
                    t = t / 24;
                    k = 9;
                }
            }
        }
        String time;
        if (t > 4 && t < 21)
            time = t + getResources().getStringArray(R.array.time)[1 + k];
        else {
            if (t == 1)
                time = getResources().getStringArray(R.array.time)[k];
            else {
                int n = (int) t % 10;
                if (n == 1)
                    time = t + " " + getResources().getStringArray(R.array.time)[k];
                else if (n > 1 && n < 5)
                    time = t + getResources().getStringArray(R.array.time)[2 + k];
                else
                    time = t + getResources().getStringArray(R.array.time)[1 + k];
            }
        }

        return time + getResources().getStringArray(R.array.time)[12];
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        findViewById(R.id.fabClear).setOnClickListener(new View.OnClickListener() {
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
        dbJournal = new DataBase(this);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        ListView lvJournal = (ListView) findViewById(R.id.lvJournal);
        adJournal = new ListAdapter(this);
        lvJournal.setAdapter(adJournal);
        lvJournal.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String link = adJournal.getItem(pos).getLink();
                boolean b = false;
                if (link.indexOf(BrowserActivity.ARTICLE) == 0) {
                    link = link.substring(BrowserActivity.ARTICLE.length());
                    b = true;
                }
                BrowserActivity.openActivity(getApplicationContext(), link, b);
                adJournal.clear();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.setCheckedItem(R.id.nav_journal);
        if (adJournal.getCount() == 0)
            createList();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
