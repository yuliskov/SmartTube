package com.liskovsoft.smartyoutubetv2.tv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by vadim on 11.12.20.
 */

public class LogUtil {
    private static final int MAX_LINES = 100;

    public static String readLog() {
        StringBuilder logBuilder = new StringBuilder();
        List<String> list = new ArrayList<>();

        try {
            Process process = Runtime.getRuntime().exec("logcat -d -v threadtime");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                list.add(line + "\n");
                if (list.size() > MAX_LINES) {
                    list.remove(0);
                }
            }

            for(Iterator<String> i = list.iterator(); i.hasNext(); ) {
                logBuilder.append(i.next());
            }
        } catch (IOException e) {}
        return logBuilder.toString();
    }
}
