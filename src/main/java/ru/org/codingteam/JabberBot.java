package ru.org.codingteam;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

import java.util.ArrayList;
import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Calendar;

@SuppressWarnings("unchecked")
public class JabberBot implements Runnable
{
    private String nick;
    private String password;
    private String domain;
    private String server;
    private int port;
    private List<String> conferences;

    private Calendar calendar;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]");

    private ConnectionConfiguration connConfig;
    private XMPPConnection connection;

    private List<MultiUserChat> userChats = new ArrayList<MultiUserChat>();

    public JabberBot (String nick, String password, String domain, String server, int port, List<String> conferences) {
        this.nick = nick;
        this.password = password;
        this.domain = domain;
        this.server = server;
        this.port = port;
        this.conferences = conferences;
    }

    @Override
    public void run() {
        connConfig = new ConnectionConfiguration(server, port, domain);
        connection = new XMPPTCPConnection(connConfig);

        try {
            SASLAuthentication.supportSASLMechanism("PLAIN", 0);
            connection.connect();
            connection.login(nick, password);

            for (String conference : conferences) {
                joinMUC(conference, "_brdg");
            }

            while(connection.isConnected()) {
                Thread.sleep(10000);
                System.out.println(sdf.format(Calendar.getInstance().getTime()) + " It's alive!");
            }
        } catch (InterruptedException | SmackException | XMPPException | java.io.IOException e) {
            e.printStackTrace();
        }
    }

    void joinMUC(String chat, String local_nick)
            throws XMPPException, SmackException, java.io.IOException {

        XMPPConnection conn = new XMPPTCPConnection(new ConnectionConfiguration(server, port, domain));
        conn.connect();
        conn.login(nick, password, local_nick);


        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);
        //MultiUserChat muc = new MultiUserChat(conn, chat);
        MultiUserChat muc = new MultiUserChat(connection, chat);
        muc.join(local_nick, "", history, 1000);
        muc.addMessageListener(new MucPacketListener());

        userChats.add(muc);
    }

    void sendMessage(String user, String message)
            throws XMPPException, SmackException.NotConnectedException {

        sendMessage(user, message, userChats);
    }

    synchronized void sendMessage(String user, String message, List<MultiUserChat> connections)
            throws XMPPException, SmackException.NotConnectedException {

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
            if (message.getSubject() == null || message.getSubject().isEmpty()) {
                String user = message.getFrom();
                if (!user.split("/")[1].startsWith("_")) {
                    String msg = message.getBody();
                    try {
                        sendMessage(user, msg);
                    } catch (XMPPException | SmackException.NotConnectedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
