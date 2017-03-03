package ru.neosvet.vestnewage;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.StatusTask;

public class StatusFragment extends Fragment {
    private final String EMAIL = "email", PASSWORD = "password";
    private MainActivity act;
    private ListAdapter adMain;
    private CheckBox cbRemEmail, cbRemPassword;
    private EditText etEmail, etPassword;
    private View container, fabEnter, fabExit;
    private String cookie;
    private StatusTask task;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.status_fragment, container, false);
        act = (MainActivity) getActivity();
        initViews();
        setViews();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), act.MODE_PRIVATE);
            String s = pref.getString(EMAIL, "");
            if (s.length() > 0) {
                cbRemEmail.setChecked(true);
                etEmail.setText(s);
                s = pref.getString(PASSWORD, "");
                if (s.length() > 0) {
                    cbRemPassword.setChecked(true);
                    etPassword.setText(uncriptPassword(s));
                }
            }
        } else {
            cookie = state.getString(Lib.COOKIE);
            task = (StatusTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                task.setFrm(this);
                act.status.setLoad(true);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(Lib.COOKIE, cookie);
        outState.putSerializable(Lib.TASK, task);
        super.onSaveInstanceState(outState);
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    private void initViews() {
        act.setTitle(getResources().getString(R.string.status));
        fabEnter = container.findViewById(R.id.fabEnter);
        fabExit = container.findViewById(R.id.fabExit);
        etEmail = (EditText) container.findViewById(R.id.etEmail);
        etPassword = (EditText) container.findViewById(R.id.etPassword);
        cbRemEmail = (CheckBox) container.findViewById(R.id.cbRemEmail);
        cbRemPassword = (CheckBox) container.findViewById(R.id.cbRemPassword);
        ListView lvList = (ListView) container.findViewById(R.id.lvList);
        adMain = new ListAdapter(act);
        lvList.setAdapter(adMain);
        defaultList();
        lvList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (adMain.getCount() == 1) {
                    adMain.clear();
                    defaultList();
                }
            }
        });
    }

    private void defaultList() {
        for (int i = 0; i < getResources().getStringArray(R.array.status).length; i += 2) {
            adMain.addItem(new ListItem(getResources().getStringArray(R.array.status)[i]),
                    getResources().getStringArray(R.array.status)[i + 1]);
        }
        adMain.notifyDataSetChanged();
    }

    private void setViews() {
        TextWatcher textWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                boolean b = false;
                if (etEmail.length() > 5 && etPassword.length() > 5) {
                    b = (etEmail.getText().toString().contains("@") && etEmail.getText().toString().contains("."));
                }
                if (b) { //ready to login
                    fabEnter.setVisibility(View.VISIBLE);
                } else {
                    fabEnter.setVisibility(View.GONE);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        };
        etEmail.addTextChangedListener(textWatcher);
        etPassword.addTextChangedListener(textWatcher);

        etPassword.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_GO) {
                    if (fabEnter.getVisibility() == View.VISIBLE)
                        subLogin();
                    return true;
                }
                return false;
            }
        });
        cbRemEmail.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                cbRemPassword.setEnabled(check);
                if (!check)
                    cbRemPassword.setChecked(false);
            }
        });
        fabEnter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                subLogin();
            }
        });
        fabExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!cbRemPassword.isChecked()) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), act.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(PASSWORD, "");
            if (!cbRemEmail.isChecked()) {
                editor.putString(EMAIL, "");
            }
            editor.commit();
        }
    }

    private String criptPassword(String password) {
        char[] c = password.toCharArray();
        password = "";
        for (int a = 0; a < c.length; a++) {
            password += Character.toString((char) ((Character.codePointAt(c, a) - a - 1)));
        }
        return password;
    }

    private String uncriptPassword(String password) {
        try {
            char[] c = password.toCharArray();
            password = "";
            for (int a = 0; a < c.length; a++) {
                password += Character.toString((char) ((Character.codePointAt(c, a) + a + 1)));
            }
            return password;
        } catch (Exception e) {
            return "";
        }
    }

    private void subLogin() {
        if (task != null)
            return;
        if (cbRemEmail.isChecked()) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), act.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(EMAIL, etEmail.getText().toString());
            if (cbRemPassword.isChecked()) {
                editor.putString(PASSWORD, criptPassword(etPassword.getText().toString()));
            }
            editor.commit();
        }
        task = new StatusTask(this);
        task.execute(etEmail.getText().toString(), etPassword.getText().toString());
        act.status.setLoad(true);
    }

    public void putResultTask(String result) {
        task = null;
        adMain.clear();
        if (result.contains(Lib.N) && !result.contains(":")) { // list word
            String[] m = result.split(Lib.N);
            for (int i = 0; i < m.length; i++) {
                adMain.addItem(new ListItem(m[i]));
            }
        } else { // message
            adMain.addItem(new ListItem(result));
        }
        adMain.notifyDataSetChanged();
        act.status.setLoad(false);
    }
}
