package ru.neosvet.vestnewage.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.vestnewage.R;

public class PresentationActivity extends AppCompatActivity {
    private static final String NAME_PREF = "presentation";
    private View bNext;
    private ImageView ivPres;
    private Resources res;
    private String pack, sid;
    private int index = 1;

    public static boolean checkPresentation(Context context, int id) {
        SharedPreferences pref = context.getSharedPreferences(NAME_PREF, context.MODE_PRIVATE);
        return !pref.getBoolean("p" + id, false);
    }

    public static void startPresentation(Context context, int id, boolean first) {
        Intent intent = new Intent(context, PresentationActivity.class);
        intent.putExtra(DataBase.ID, id);
        intent.putExtra(Const.FIRST, first);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.presentation_activity);

        int id = getIntent().getIntExtra(DataBase.ID, -1);
        if (id == -1) {
            finish();
            return;
        }
        sid = "p" + id;
        if (getIntent().getBooleanExtra(Const.FIRST, false)) {
            SharedPreferences pref = getSharedPreferences(NAME_PREF, MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean(sid, true);
            editor.commit();
        }
        res = getResources();
        pack = getPackageName();
        ivPres = (ImageView) findViewById(R.id.ivPresentation);
        bNext = findViewById(R.id.bNext);

        if (savedInstanceState != null)
            index = savedInstanceState.getInt(DataBase.ID);

        setImage();
        findViewById(R.id.bFinish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        bNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                index++;
                setImage();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(DataBase.ID, index);
        super.onSaveInstanceState(outState);
    }

    public void setImage() {
        ivPres.setImageResource(getIdImage(index));
        if (getIdImage(index + 1) == 0) {
            findViewById(R.id.divPres).setVisibility(View.GONE);
            bNext.setVisibility(View.GONE);
        }
    }

    public int getIdImage(int i) {
        return res.getIdentifier(sid + "_" + i, "drawable", pack);
    }
}
