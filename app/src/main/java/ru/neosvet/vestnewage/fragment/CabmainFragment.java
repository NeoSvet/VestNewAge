package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.app.Service;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.activity.CabpageActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.task.CabTask;
import ru.neosvet.utils.Lib;

public class CabmainFragment extends Fragment {
    private final String EMAIL = "email", PASSWORD = "password", PANEL = "panel";
    private final byte LOGIN = 0, ENTER = 1, WORDS = 2;
    private MainActivity act;
    private ListAdapter adMain;
    private SoftKeyboard softKeyboard;
    private CheckBox cbRemEmail, cbRemPassword;
    private TextView tvError;
    private EditText etEmail, etPassword;
    private View container, fabEnter, fabExit, pMain, divCab;
    private String cookie = "";
    private byte mode_list = 0;
    private CabTask task;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.cabmain_fragment, container, false);
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
            loginList();
        } else {
            act.setFrCabinet(this);
            cookie = state.getString(Const.COOKIE);
            task = (CabTask) state.getSerializable(Const.TASK);
            if (task != null) {
                if (task.getStatus() == AsyncTask.Status.RUNNING) {
                    task.setFrm(this);
                    act.status.setLoad(true);
                } else task = null;
            }
            mode_list = state.getByte(PANEL);
            if (mode_list > LOGIN) {
                pMain.setVisibility(View.GONE);
                divCab.setVisibility(View.GONE);
                fabEnter.setVisibility(View.GONE);
                fabExit.setVisibility(View.VISIBLE);
            }
            String d;
            for (String t : state.getStringArray(Const.LINK)) {
                if (t.contains("#")) {
                    d = t.substring(t.indexOf("#") + 1);
                    t = t.substring(0, t.indexOf("#"));
                    adMain.addItem(new ListItem(t), d);
                } else
                    adMain.addItem(new ListItem(t));
            }
        }
        adMain.notifyDataSetChanged();
        softKeyboard.closeSoftKeyboard();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(Const.COOKIE, cookie);
        outState.putSerializable(Const.TASK, task);
        outState.putByte(PANEL, mode_list);
        String[] m = new String[adMain.getCount()];
        String d;
        for (int i = 0; i < adMain.getCount(); i++) {
            d = adMain.getItem(i).getDes();
            m[i] = adMain.getItem(i).getTitle() + (d == null ? "" : "#" + d);
        }
        outState.putStringArray(Const.LINK, m);
        super.onSaveInstanceState(outState);
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    private void initViews() {
        act.setTitle(getResources().getString(R.string.cabinet));
        pMain = container.findViewById(R.id.pMain);
        divCab = container.findViewById(R.id.divCab);
        fabEnter = container.findViewById(R.id.fabEnter);
        fabExit = container.findViewById(R.id.fabExit);
        tvError = (TextView) container.findViewById(R.id.tvError);
        etEmail = (EditText) container.findViewById(R.id.etEmail);
        etPassword = (EditText) container.findViewById(R.id.etPassword);
        cbRemEmail = (CheckBox) container.findViewById(R.id.cbRemEmail);
        cbRemPassword = (CheckBox) container.findViewById(R.id.cbRemPassword);
        InputMethodManager im = (InputMethodManager) act.getSystemService(Service.INPUT_METHOD_SERVICE);
        LinearLayout mainLayout = (LinearLayout) container.findViewById(R.id.content_cabmain);
        softKeyboard = new SoftKeyboard(mainLayout, im);
        ListView lvList = (ListView) container.findViewById(R.id.lvList);
        adMain = new ListAdapter(act);
        lvList.setAdapter(adMain);
        lvList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (act.status.isVis()) return;
                if (mode_list == LOGIN) { //до кабинета
                    String s;
                    switch (pos) {
                        case 0: //восстановить доступ
                            s = "sendpass.html";
                            break;
                        case 1: //зарегистрироваться
                            s = "register.html";
                            break;
                        case 2: //о регистрации
                            s = "reginfo.html";
                            break;
                        case 3: //стастистика регистраций
                            s = "regstat.html";
                            break;
                        default: //(4) стастистика слов
                            s = "trans.html";
                            break;
                    }
                    CabpageActivity.openPage(act, s, null);
                } else if (mode_list == ENTER) { // в кабинете
                    switch (pos) {
                        case 0: //передача ощущений
                            if (adMain.getItem(pos).getDes().equals(
                                    getResources().getString(R.string.select_status))
                                    || adMain.getItem(pos).getDes().contains(
                                    getResources().getString(R.string.selected))) {
                                //get list words
                                task = new CabTask(CabmainFragment.this);
                                task.execute(cookie);
                                act.status.setLoad(true);
                            } else
                                Lib.showToast(act, getResources().getString(R.string.send_unlivable));
                            break;
                        case 1: //анкета
                            CabpageActivity.openPage(act, "edinenie/anketa.html", cookie);
                            break;
                        case 2: //единомышленники
                            CabpageActivity.openPage(act, "edinenie/edinomyshlenniki.html", cookie);
                            break;
                        default:
                            break;
                    }
                } else if (mode_list == WORDS) { //выбор слова
                    task = new CabTask(CabmainFragment.this);
                    task.execute(etEmail.getText().toString(), cookie, String.valueOf(pos));
                    act.status.setLoad(true);
                }
            }
        });
    }

    private void loginList() {
        tvError.setVisibility(View.GONE);
        for (int i = 0; i < getResources().getStringArray(R.array.cabinet_main).length; i += 2) {
            adMain.addItem(new ListItem(getResources().getStringArray(R.array.cabinet_main)[i]),
                    getResources().getStringArray(R.array.cabinet_main)[i + 1]);
        }
        adMain.notifyDataSetChanged();
    }

    private void setViews() {
        TextWatcher textWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                boolean b = false;
                if (etEmail.length() > 5 && etPassword.length() > 5) {
                    b = (etEmail.getText().toString().contains("@")
                            && etEmail.getText().toString().contains("."));
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
                mode_list = LOGIN;
                cookie = "";
                adMain.clear();
                loginList();
                fabEnter.setVisibility(View.VISIBLE);
                fabExit.setVisibility(View.GONE);
                pMain.setVisibility(View.VISIBLE);
                divCab.setVisibility(View.VISIBLE);
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
        softKeyboard.closeSoftKeyboard();
        if (task != null)
            return;
        if (adMain.getCount() == 1) {
            adMain.clear();
            loginList();
        }
        if (cbRemEmail.isChecked()) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), act.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(EMAIL, etEmail.getText().toString());
            if (cbRemPassword.isChecked()) {
                editor.putString(PASSWORD, criptPassword(etPassword.getText().toString()));
            }
            editor.commit();
        }
        task = new CabTask(this);
        task.execute(etEmail.getText().toString(), etPassword.getText().toString());
        act.status.setLoad(true);
    }

    public void putResultTask(String result) {
        task = null;
        act.status.setLoad(false);
        if (result.indexOf(Const.AND) == 0) {
            mode_list = ENTER;
            adMain.clear();
            adMain.notifyDataSetChanged();
            pMain.setVisibility(View.GONE);
            divCab.setVisibility(View.GONE);
            fabEnter.setVisibility(View.GONE);
            fabExit.setVisibility(View.VISIBLE);
            tvError.setText(result.substring(1));
            tvError.setVisibility(View.VISIBLE);
            return;
        }
        if (result.equals(getResources().getString(R.string.load_fail))) {
            Lib.showToast(act, result);
            return;
        }
        mode_list++;
        if (mode_list == WORDS) {
            adMain.clear();
            String[] m = result.split(Const.N);
            for (int i = 0; i < m.length; i++) {
                adMain.addItem(new ListItem(m[i]));
            }
        } else { // ENTER or after select word
            if (mode_list == ENTER) {
                pMain.setVisibility(View.GONE);
                divCab.setVisibility(View.GONE);
                fabEnter.setVisibility(View.GONE);
                fabExit.setVisibility(View.VISIBLE);
            } else {// режим списка - в кабинете
                mode_list = ENTER;
                if (result.indexOf("ok") == 0) { //слово выбрано успешно
                    result = act.getResources().getString(R.string.selected) + " " +
                            adMain.getItem(Integer.parseInt(result.substring(2))).getTitle();
                }
            }
            adMain.clear();
            for (int i = 0; i < getResources().getStringArray(R.array.cabinet_enter).length; i++) {
                adMain.addItem(new ListItem(getResources().getStringArray(R.array.cabinet_enter)[i]));
            }
            adMain.getItem(0).setDes(result);
            adMain.getItem(adMain.getCount() - 1).setDes(getResources().getString(R.string.cabinet_tip));
        }
        adMain.notifyDataSetChanged();
    }

    public boolean onBackPressed() {
        if (mode_list == LOGIN)
            return true;
        else {
            mode_list--;
            if (mode_list == LOGIN) {
                pMain.setVisibility(View.VISIBLE);
                divCab.setVisibility(View.VISIBLE);
                fabEnter.setVisibility(View.VISIBLE);
                fabExit.setVisibility(View.GONE);
                adMain.clear();
                loginList();
            } else { //ENTER
                mode_list = LOGIN;
                task = new CabTask(this);
                task.execute(etEmail.getText().toString(), etPassword.getText().toString());
                act.status.setLoad(true);
            }
            return false;
        }
    }
}
