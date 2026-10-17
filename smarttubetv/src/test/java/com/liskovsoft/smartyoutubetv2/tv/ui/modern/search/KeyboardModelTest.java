package com.liskovsoft.smartyoutubetv2.tv.ui.modern.search;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class KeyboardModelTest {
    @Test
    public void letters_gridHasSevenColumnsAndSpecialKeys() {
        List<KeyboardModel.Key> keys = KeyboardModel.grid(false);

        assertEquals(4 * 7 + 3, keys.size());
        assertEquals('a', keys.get(0).value);
        assertEquals(KeyboardModel.TYPE_BACKSPACE, keys.get(7).type);
        assertEquals('h', keys.get(8).value);
        assertEquals(KeyboardModel.TYPE_MODE, keys.get(15).type);
        assertEquals(KeyboardModel.TYPE_SYSTEM_KEYBOARD, keys.get(23).type);
        assertEquals('\'', keys.get(keys.size() - 1).value);
    }

    @Test
    public void symbols_startWithDigits() {
        assertEquals('1', KeyboardModel.grid(true).get(0).value);
    }

    @Test
    public void apply_editsText() {
        List<KeyboardModel.Key> grid = KeyboardModel.grid(false);
        List<KeyboardModel.Key> actions = KeyboardModel.actions();

        String text = KeyboardModel.apply(null, grid.get(0));
        text = KeyboardModel.apply(text, grid.get(1));
        assertEquals("ab", text);

        text = KeyboardModel.apply(text, actions.get(0));
        text = KeyboardModel.apply(text, actions.get(0));
        assertEquals("ab ", text);

        text = KeyboardModel.apply(text, grid.get(7));
        assertEquals("ab", text);

        assertEquals("", KeyboardModel.apply(text, actions.get(1)));
        assertEquals("", KeyboardModel.apply("", grid.get(7)));
        assertEquals("", KeyboardModel.apply("", actions.get(0)));
    }

    @Test
    public void label_isUppercase() {
        assertEquals("A", KeyboardModel.label(KeyboardModel.grid(false).get(0)));
    }
}
