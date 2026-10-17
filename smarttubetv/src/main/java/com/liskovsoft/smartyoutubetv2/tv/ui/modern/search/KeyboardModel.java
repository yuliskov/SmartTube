package com.liskovsoft.smartyoutubetv2.tv.ui.modern.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * On-screen keyboard: 4 rows of 7 character keys plus one special key per row, and an action row.
 */
public final class KeyboardModel {
    public static final int TYPE_CHAR = 0;
    public static final int TYPE_BACKSPACE = 1;
    public static final int TYPE_MODE = 2;
    public static final int TYPE_SYSTEM_KEYBOARD = 3;
    public static final int TYPE_SPACE = 4;
    public static final int TYPE_CLEAR = 5;
    public static final int TYPE_SEARCH = 6;
    public static final int COLUMNS = 7;
    private static final String[] LETTERS = {"abcdefg", "hijklmn", "opqrstu", "vwxyz-'"};
    private static final String[] SYMBOLS = {"1234567", "890.,@#", "!?&/:_(", ")\"+=*%$"};
    private static final int[] SPECIAL = {TYPE_BACKSPACE, TYPE_MODE, TYPE_SYSTEM_KEYBOARD};

    public static final class Key {
        public final int type;
        public final char value;
        public final int row;
        public final int column;

        Key(int type, char value, int row, int column) {
            this.type = type;
            this.value = value;
            this.row = row;
            this.column = column;
        }
    }

    private KeyboardModel() {
    }

    /**
     * Character grid (column 0..6) and the special column (column 7). Rows 0..3.
     */
    public static List<Key> grid(boolean symbols) {
        String[] rows = symbols ? SYMBOLS : LETTERS;
        List<Key> keys = new ArrayList<>();

        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                keys.add(new Key(TYPE_CHAR, rows[row].charAt(column), row, column));
            }
            if (row < SPECIAL.length) {
                keys.add(new Key(SPECIAL[row], ' ', row, COLUMNS));
            }
        }

        return Collections.unmodifiableList(keys);
    }

    public static List<Key> actions() {
        List<Key> keys = new ArrayList<>();
        keys.add(new Key(TYPE_SPACE, ' ', 4, 0));
        keys.add(new Key(TYPE_CLEAR, ' ', 4, 1));
        keys.add(new Key(TYPE_SEARCH, ' ', 4, 2));
        return Collections.unmodifiableList(keys);
    }

    /**
     * @return text after pressing the key (unchanged for non editing keys)
     */
    public static String apply(String text, Key key) {
        String current = text != null ? text : "";

        switch (key.type) {
            case TYPE_CHAR:
                return current + key.value;
            case TYPE_SPACE:
                return current.isEmpty() || current.endsWith(" ") ? current : current + " ";
            case TYPE_BACKSPACE:
                return current.isEmpty() ? current : current.substring(0, current.length() - 1);
            case TYPE_CLEAR:
                return "";
            default:
                return current;
        }
    }

    public static String label(Key key) {
        return String.valueOf(Character.toUpperCase(key.value));
    }
}
