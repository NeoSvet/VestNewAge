package ru.neosvet.vestnewage;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class HelpFragment extends Fragment {
    private final String N_LOG = "n";
    private MainActivity act;
    private View container, ivPrev, ivNext;
    private TextView tvChangelog;
    private Button bChangelog;
    private int n_log = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.fragment_help, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.help));
        initViews();
        setViews();
        if (savedInstanceState != null) {
            int n = savedInstanceState.getInt(N_LOG);
            if (n > -1)
                changeLog(n);
        }
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(N_LOG, n_log);
        super.onSaveInstanceState(outState);
    }

    private void initViews() {
        TextView tv = (TextView) container.findViewById(R.id.tvNumVer);
        try {
            tv.setText(getResources().getString(R.string.publication) +
                    act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        tvChangelog = (TextView) container.findViewById(R.id.tvChangeLog);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        bChangelog = (Button) container.findViewById(R.id.bChangeLog);
    }

    private void setViews() {
        container.findViewById(R.id.bWriteMail).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                act.lib.openInApps("mailto:neosvet333@gmail.com");
            }
        });
        bChangelog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (n_log == -1) {
                    changeLog(0);
                } else {
                    n_log = -1;
                    bChangelog.setText(getResources().getString(R.string.show_changelog));
                    tvChangelog.setVisibility(View.GONE);
                    ivPrev.setVisibility(View.GONE);
                    ivNext.setVisibility(View.GONE);
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
    }

    private void changeLog(int n) {
        if (n_log == -1) {
            bChangelog.setText(getResources().getString(R.string.hide_changelog));
            tvChangelog.setVisibility(View.VISIBLE);
            ivPrev.setVisibility(View.VISIBLE);
            ivNext.setVisibility(View.VISIBLE);
        }
        ivNext.setEnabled(n > 0);
        int k = getResources().getStringArray(R.array.changelog).length;
        ivPrev.setEnabled(n < k - 1);
        tvChangelog.setText(getResources().getString(R.string.publication) + (k - n)
                + ":\n" + getResources().getStringArray(R.array.changelog)[n]);
        n_log = n;
    }
}
