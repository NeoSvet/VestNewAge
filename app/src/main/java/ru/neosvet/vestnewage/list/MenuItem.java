package ru.neosvet.vestnewage.list;

public class MenuItem {
    private String title;
    private int image;
    private boolean select = false;

    public boolean isSelect() {
        return select;
    }

    public void setSelect(boolean select) {
        this.select = select;
    }

    public int getImage() {
        return image;
    }

    public String getTitle() {
        return title;
    }

    public MenuItem(int image, String title) {
        this.image = image;
        this.title = title;

    }
}
