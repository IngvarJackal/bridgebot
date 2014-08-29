package ru.org.codingteam;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.DiscussionHistory;

import java.util.*;

public class Main
{
    public static void main(String[] args)
    {
        try
        {
            String botNick = "ivrbot";
            String botPassword = "furry1234";
            String botDomain = "jabber.ru";
            String botServer = "jabber.ru";
            int botPort = 5222;

            JabberBot bot = new JabberBot(botNick, botPassword, botDomain, botServer, botPort);
            Thread botThread = new Thread(bot);
            botThread.start();
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

class JabberBot implements Runnable
{
    private String nick;
    private String password;
    private String domain;
    private String server;
    private int port;

    private ConnectionConfiguration connConfig;
    private XMPPConnection connection;

    private List<MultiUserChat> userChats = new ArrayList<MultiUserChat>();

    public JabberBot (String nick, String password, String domain, String server, int port)
    {
        this.nick = nick;
        this.password = password;
        this.domain = domain;
        this.server = server;
        this.port = port;
    }

    @Override
    public void run()
    {
        connConfig = new ConnectionConfiguration(server, port, domain);
        connection = new XMPPTCPConnection(connConfig);

        try
        {
            int priority = 10;
            SASLAuthentication.supportSASLMechanism("PLAIN", 0);
            connection.connect();
            connection.login(nick, password);

            joinMUC("furry@conference.jabber.ru", "_brdg");
            joinMUC("furry@conference.dukgo.com", "_brdg");

            while(connection.isConnected())
            {
                Thread.sleep(10000);
                System.out.println("It's alive!");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    void joinMUC(String chat, String local_nick) throws Exception {
        /*
        XMPPConnection conn = new XMPPTCPConnection(new ConnectionConfiguration(server, port, domain));
        conn.connect();
        conn.login(nick, password, local_nick);
        */

        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);
        //MultiUserChat muc = new MultiUserChat(conn, chat);
        MultiUserChat muc = new MultiUserChat(connection, chat);
        muc.join(local_nick, "", history, 1000);
        muc.addMessageListener(new MucPacketListener());

        userChats.add(muc);
    }

    void sendMessage(String user, String message) throws Exception {
        sendMessage(user, message, userChats);
    }

    synchronized void sendMessage(String user, String message, List<MultiUserChat> connections) throws Exception {
        for (MultiUserChat connection : connections) {
            //Presence presence = connection.getOccupantPresence(connection.getRoom() + "/" + user.split("/")[1]);
            //if (presence == null ||  presence.isAway()) {
            if (!connection.getRoom().equals(user.split("/")[0])) {
                StringBuilder domain = new StringBuilder();
                for (String dom : user.split("/")[0].split("\\.")) {
                    domain.append(dom.charAt(0));
                }
                connection.sendMessage(user.split("/")[1] + "@" + domain.toString()  + ": " + message);
            }
        }
    }

    class MucPacketListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            Message message = (Message)packet;
            String user = message.getFrom();
            if (!user.split("/")[1].startsWith("_")) {
                String msg = message.getBody();
                try {
                    sendMessage(user, msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
