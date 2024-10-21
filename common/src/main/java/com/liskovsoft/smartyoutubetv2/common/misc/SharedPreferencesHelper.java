package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class SharedPreferencesHelper {
    private static final String TAG = SharedPreferencesHelper.class.getSimpleName();

    // Backup SharedPreferences to a file
    public static void backupSharedPrefs(Context context, File backupFile) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            Map<String, ?> allEntries = prefs.getAll();

            try (FileOutputStream fos = new FileOutputStream(backupFile);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(allEntries);
            }

            Log.d(TAG, "Backup completed successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to backup SharedPreferences: " + e.getMessage());
        }
    }

    // Restore SharedPreferences from a backup file
    public static void restoreFromObj(Context context, InputStream backupFile, String preferenceName) {
        if (preferenceName.endsWith(".xml")) {
            preferenceName = preferenceName.replace(".xml", "");
        }

        try {
            // Read the backup file
            Map<String, ?> restoredData;
            try (ObjectInputStream ois = new ObjectInputStream(backupFile)) {
                //noinspection unchecked
                restoredData = (Map<String, ?>) ois.readObject();
            }

            // Get SharedPreferences instance (creates a new one if it doesn't exist)
            SharedPreferences prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Optional: Clear existing preferences before restoring
            editor.clear();

            // Restore key-value pairs to SharedPreferences
            for (Map.Entry<String, ?> entry : restoredData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else {
                    Log.e(TAG, "Unsupported data type: " + key + " -> " + value);
                }
            }

            // Apply the changes
            editor.apply();
            Log.d(TAG, "SharedPreferences restored successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore SharedPreferences: " + e.getMessage());
        }
    }

    // Restore SharedPreferences from an XML file
    public static void restoreFromXml(Context context, InputStream backupFile, String preferenceName) {
        if (preferenceName.endsWith(".xml")) {
            preferenceName = preferenceName.replace(".xml", "");
        }

        try {
            // Parse the XML file
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(backupFile);
            doc.getDocumentElement().normalize();

            // Get SharedPreferences editor
            SharedPreferences prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear(); // Optional: Clear existing preferences

            NodeList mapNode = doc.getElementsByTagName("map");

            if (mapNode.getLength() == 0) {
                throw new IllegalStateException("Shared prefs format doesn't contain map item");
            }

            if (mapNode.getLength() > 1) {
                throw new IllegalStateException("Shared prefs contains more than one map item");
            }

            // Iterate over <entry> elements
            NodeList nodeList = mapNode.item(0).getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node item = nodeList.item(i);

                if (item.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element element = (Element) item;
                String key = element.getAttribute("name");
                String type = element.getNodeName();
                String value = "string".equals(type) ? element.getTextContent() : element.getAttribute("value");

                // Restore the value based on its type
                switch (type) {
                    case "string":
                        editor.putString(key, value);
                        break;
                    case "int":
                        editor.putInt(key, Integer.parseInt(value));
                        break;
                    case "boolean":
                        editor.putBoolean(key, Boolean.parseBoolean(value));
                        break;
                    case "float":
                        editor.putFloat(key, Float.parseFloat(value));
                        break;
                    case "long":
                        editor.putLong(key, Long.parseLong(value));
                        break;
                    default:
                        Log.d(TAG, "Unsupported data type: " + type);
                }
            }

            editor.apply();
            Log.d(TAG, "SharedPreferences restored successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore SharedPreferences: " + e.getMessage());
        }
    }
}

