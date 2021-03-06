package ru.neosvet.vestnewage.list;

public class CheckItem {
    private final String title;
    private int id = 0;
    private boolean check = false;

    public CheckItem(int id, String title) {
        this.id = id;
        this.title = title;
    }

    public CheckItem(String title) {
        this.title = title;
    }

    public boolean isCheck() {
        return check;
    }

    public void setCheck(boolean check) {
        this.check = check;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }
}
