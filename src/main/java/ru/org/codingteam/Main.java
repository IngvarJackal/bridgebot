package ru.org.codingteam;

import org.yaml.snakeyaml.Yaml;

import java.io.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    private static String botNick;
    private static String botPassword;
    private static String botDomain;
    private static String botServer;
    private static int botPort;
    private static List<String> botConferences = new ArrayList<String>();

    public static void main(String[] args) {
        loadPreferences();
        try {
            JabberBot bot = new JabberBot(botNick, botPassword, botDomain, botServer, botPort, botConferences);
            Thread botThread = new Thread(bot);
            botThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadPreferences() {
        Yaml yaml = new Yaml();
        InputStream input = null;
        try {
            input = new FileInputStream(new File("bot.prefs.yml"));
            Map<String,Object> data = (Map<String,Object>)yaml.load(input);
            botNick = (String)data.get("botNick");
            botPassword = (String)data.get("botPassword");
            botDomain = (String)data.get("botDomain");
            botServer = (String)data.get("botServer");
            botPort = (int)data.get("botPort");
            botConferences = (ArrayList)data.get("botConferences");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(18);
        } finally {
            try {
                input.close();
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }
        }
    }
}

