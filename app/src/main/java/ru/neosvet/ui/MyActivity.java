package ru.neosvet.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import ru.neosvet.blagayavest.BookActivity;
import ru.neosvet.blagayavest.CalendarActivity;
import ru.neosvet.blagayavest.HelpActivity;
import ru.neosvet.blagayavest.JournalActivity;
import ru.neosvet.blagayavest.MainActivity;
import ru.neosvet.blagayavest.R;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.LoaderTask;

/**
 * Created by NeoSvet on 19.12.2016.
 */

public class MyActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private LoaderTask loader = null;
    private final String LOADER = "loader";
    public Lib lib = new Lib(this);

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(LOADER, loader);
        super.onSaveInstanceState(outState);
    }

    protected void restoreActivityState(Bundle state) {
        if (state != null) {
            loader = (LoaderTask) state.getSerializable(LOADER);
            if (loader != null) {
                loader.setAct(this);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_table, menu);
        if (this instanceof JournalActivity || this instanceof HelpActivity)
            return true;
        MenuItem item = menu.add(getResources().getString(R.string.download_all));
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        item.setIcon(R.drawable.download);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int curId = -1;
        Class[] actM = new Class[]{MainActivity.class, CalendarActivity.class,
                BookActivity.class, JournalActivity.class, HelpActivity.class};
        for (int i = 0; i < actM.length; i++) {
            if (this.getClass().equals(actM[i])) {
                curId = i;
                break;
            }
        }
//        Lib.LOG("curId=" + curId);
        if (curId == item.getItemId() || curId == -1)
            return true;
        Intent intent = null;
        item.setCheckable(true);
        switch (item.getItemId()) {
            case R.id.nav_rss:
                break;
            case R.id.nav_main:
                intent = new Intent(this, MainActivity.class);
                break;
            case R.id.nav_calendar:
                intent = new Intent(this, CalendarActivity.class);
                break;
            case R.id.nav_book:
                intent = new Intent(this, BookActivity.class);
                break;
            case R.id.nav_journal:
                intent = new Intent(this, JournalActivity.class);
                break;
            case R.id.nav_search:
                break;
            case R.id.nav_settings:
                break;
            case R.id.nav_help:
                intent = new Intent(this, HelpActivity.class);
                break;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void downloadAll() {
        loader = new LoaderTask(this);
        loader.execute();
    }

    public void finishAllLoad(Boolean suc) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (suc)
            builder.setMessage(getResources().getString(R.string.all_load_suc));
        else
            builder.setMessage(getResources().getString(R.string.all_load_fail));
        builder.setPositiveButton(getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        builder.create().show();
    }
}
