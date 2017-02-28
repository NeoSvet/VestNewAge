package ru.neosvet.blagayavest;

import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import ru.neosvet.ui.MyActivity;
import ru.neosvet.utils.Lib;

/**
 * Created by NeoSvet on 28.12.2016.
 */

public class HelpActivity extends MyActivity {
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        initViews();
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        TextView tv = (TextView) findViewById(R.id.tvNumVer);
        try {
            tv.setText(getResources().getString(R.string.publication) +
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        findViewById(R.id.bWriteMail).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Lib(HelpActivity.this).openInApps("mailto:neosvet333@gmail.com");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.setCheckedItem(R.id.nav_help);
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
