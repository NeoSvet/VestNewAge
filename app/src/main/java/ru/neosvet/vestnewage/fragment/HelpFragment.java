package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.HelpAdapter;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;

public class HelpFragment extends Fragment {
    private final String N_LOG = "n", HELP = "help";
    private final int COUNT = 7;
    private MainActivity act;
    private boolean boolAnim = false;
    private View container, ivPrev, ivNext, pInfo;
    private ListView lvHelp;
    private HelpAdapter adHelp;
    private TextView tvChangelog, tvHelp, tvChangelogTitle;
    private boolean[] mHelp = null;
    private int n_log;
    private Animation anMin, anMax;
    private Handler hShow = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            tvHelp.setVisibility(View.VISIBLE);
            tvHelp.startAnimation(anMax);
            boolAnim = false;
            return false;
        }
    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.help_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.help));
        initViews();
        setViews();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    public void setOpenHelp(int i) {
        mHelp = new boolean[COUNT];
        mHelp[i] = true;
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            n_log = 0;
            if (mHelp == null)
                mHelp = new boolean[COUNT];
        } else {
            n_log = state.getInt(N_LOG);
            mHelp = state.getBooleanArray(Const.LINK);
            adHelp.notifyDataSetChanged();
            if (!state.getBoolean(HELP)) {
                lvHelp.setVisibility(View.GONE);
                pInfo.setVisibility(View.VISIBLE);
                tvHelp.setText("?");
            }
        }
        changeLog(n_log);
        for (int i = 0; i < COUNT; i++) {
            adHelp.addItem(getResources().getStringArray(R.array.help_title)[i],
                    getResources().getStringArray(R.array.help_content)[i]);
            if (mHelp[i])
                adHelp.getItem(i).addLink("");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(N_LOG, n_log);
        outState.putBooleanArray(Const.LINK, mHelp);
        outState.putBoolean(HELP, lvHelp.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    private void initViews() {
        TextView tv = (TextView) container.findViewById(R.id.tvNumVer);
        try {
            tv.setText(getResources().getString(R.string.current_publication) +
                    act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                boolAnim = true;
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                tvHelp.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
        tvChangelog = (TextView) container.findViewById(R.id.tvChangeLog);
        tvHelp = (TextView) container.findViewById(R.id.tvHelp);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        tvChangelogTitle = (TextView) container.findViewById(R.id.tvChangelogTitle);
        pInfo = container.findViewById(R.id.pInfo);
        lvHelp = (ListView) container.findViewById(R.id.lvHelp);
        adHelp = new HelpAdapter(act);
    }

    private void setViews() {
        container.findViewById(R.id.bWriteMail).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                act.lib.openInApps("mailto:neosvet333@gmail.com", null);
            }
        });
        container.findViewById(R.id.bAppLink).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                act.lib.copyAddress(getResources().getString(R.string.url_on_app));
            }
        });
        tvHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tvHelp.getText().equals("i")) {
                    lvHelp.setVisibility(View.GONE);
                    pInfo.setVisibility(View.VISIBLE);
                    tvHelp.setText("?");
                } else {
                    lvHelp.setVisibility(View.VISIBLE);
                    pInfo.setVisibility(View.GONE);
                    tvHelp.setText("i");
                }
            }
        });
        ivPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeLog(n_log + 1);
            }
        });
        ivNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeLog(n_log - 1);
            }
        });
        lvHelp.setAdapter(adHelp);
        lvHelp.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (adHelp.getItem(pos).getCount() == 0) {
                    mHelp[pos] = true;
                    adHelp.getItem(pos).addLink("");
                } else {
                    mHelp[pos] = false;
                    adHelp.getItem(pos).clear();
                }
                adHelp.notifyDataSetChanged();
            }
        });
        lvHelp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                    hideButton();
                return false;
            }
        });
        tvChangelog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideButton();
            }
        });
    }

    private void hideButton() {
        if (boolAnim) return;
        tvHelp.startAnimation(anMin);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                hShow.sendEmptyMessage(0);
            }
        }, 1500);
    }

    private void changeLog(int n) {
        ivNext.setEnabled(n > 0);
        int k = getResources().getStringArray(R.array.changelog).length;
        ivPrev.setEnabled(n < k - 1);
        tvChangelogTitle.setText(getResources().getString(R.string.changelog) + Const.N
                + getResources().getString(R.string.publication) + (k - n));
        tvChangelog.setText(getResources().getStringArray(R.array.changelog)[n]);
        n_log = n;
    }
}
