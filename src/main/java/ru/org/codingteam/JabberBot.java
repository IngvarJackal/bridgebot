package ru.org.codingteam;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.ParticipantStatusListener;

import java.io.IOException;
import java.util.*;

import java.text.SimpleDateFormat;

@SuppressWarnings("unchecked")
public class JabberBot implements Runnable
{
    private final String BOTNAME = "_brdg"; // should start from underscore!

    private String nick;
    private String password;
    private String domain;
    private String server;
    private int port;
    private List<String> conferences;

    private final static SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]");

    private ConnectionConfiguration connConfig;
    private XMPPConnection connection;

    private HashMap<String, HashMap<String, MultiUserChat>> listOfMUCs = new HashMap<>();
    private Set<String> users = Collections.synchronizedSet(new HashSet<String>());

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
                joinMUC(conference, BOTNAME, true);
                for (String occupant : listOfMUCs.get(conference).get(BOTNAME).getOccupants()) {
                    if (!StringUtils.parseResource(occupant).startsWith("_")) {
                        users.add(occupant);
                    }
                }
            }
            for (String conference : conferences) {
                for (String user : users) {
                    if (!StringUtils.parseBareAddress(user).equals(conference)) {
                        joinMUC(conference, user);
                    }
                }
            }

            while(connection.isConnected()) {
                Thread.sleep(10000);
                System.out.println(sdf.format(Calendar.getInstance().getTime()) + " It's alive!");
            }
        } catch (InterruptedException | SmackException | XMPPException | java.io.IOException e) {
            e.printStackTrace();
        }
    }

    void joinMUC(String chat, String roomNick)
            throws XMPPException, SmackException, java.io.IOException {
        joinMUC(chat, roomNick, false);
    }

    synchronized void joinMUC(String chat, String roomNick, boolean makeListener)
            throws XMPPException, SmackException, java.io.IOException {
        String localNick = formatNick4MUC(roomNick);
        XMPPConnection conn = new XMPPTCPConnection(new ConnectionConfiguration(server, port, domain));
        conn.connect();
        conn.login(nick, password, chat + "/" + localNick);

        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);
        MultiUserChat muc = new MultiUserChat(conn, chat);
        while (true) {
            try {
                muc.join(localNick, "", history, 1000);
                break;
            } catch (SmackException e) {
                // just ignore?
            }
        }
        if (makeListener) {
            muc.addMessageListener(new MucPacketListener());
            muc.addParticipantStatusListener(new MucParticipantListener());
        }

        if (!listOfMUCs.containsKey(chat)) {
            listOfMUCs.put(chat, new HashMap<String, MultiUserChat>());
        }
        listOfMUCs.get(chat).put(localNick, muc);
    }

    synchronized void leaveMUC(String roomNick) throws SmackException.NotConnectedException {
        String localNick = formatNick4MUC(roomNick);
        for (String conference : listOfMUCs.keySet()) {
            MultiUserChat conn = listOfMUCs.get(conference).remove(localNick);
            if (conn != null) {
                System.out.println(conn);
                conn.leave(); // TODO: java.lang.reflect.InvocationTargetException caused by: java.lang.NullPointerException -- WTF???
            }
        }
    }

    void sendMessage(String user, String message)
            throws XMPPException, SmackException.NotConnectedException {
        sendMessage(user, message, listOfMUCs);
    }

    synchronized void sendMessage(String user, String message, HashMap<String, HashMap<String, MultiUserChat>> mucs)
            throws XMPPException, SmackException.NotConnectedException {
        for (String conference : mucs.keySet()) {
            if (!conference.equals(StringUtils.parseBareAddress(user))) {
                String formatted = formatNick(user);
                mucs.get(conference).get(formatted).sendMessage(message);
            }
        }
    }

    void changeNick(String user, String newNick) throws SmackException.NotConnectedException, XMPPException.XMPPErrorException, SmackException.NoResponseException {
        changeNick(user, newNick, listOfMUCs);
    }

    synchronized void changeNick(String user, String newNick, HashMap<String, HashMap<String, MultiUserChat>> mucs)
            throws SmackException.NotConnectedException, XMPPException.XMPPErrorException, SmackException.NoResponseException {
        for (String conference : mucs.keySet()) {
            if (!conference.equals(StringUtils.parseBareAddress(user))) {
                String formatted = formatNick(user);
                String newFormattedNick = newNick + "@" + formatted.split("@")[formatted.split("@").length - 1];
                mucs.get(conference).get(formatted).changeNickname(newFormattedNick); // TODO: java.lang.reflect.InvocationTargetException caused by: java.lang.NullPointerException -- same issue as above -- WTF???
                mucs.get(conference).put(newFormattedNick, mucs.get(conference).remove(formatted));
                System.out.println(mucs);
            }
        }
    }

    class MucPacketListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            Message message = (Message)packet;
            if (message.getSubject() == null || message.getSubject().isEmpty()) {
                String user = message.getFrom();
                String name = StringUtils.parseResource(user);
                if (!name.contains("@") ||
                        (name.contains("@") &&
                         name.split("@")[name.split("@").length - 1].length() > 3)) {
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

    class MucParticipantListener implements ParticipantStatusListener {
        private void participantLeave(String participant) {
            try {
                leaveMUC(participant);
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void joined(String participant) {
            try {
                String name = StringUtils.parseResource(participant);
                if (!name.contains("@") ||
                        (name.contains("@") &&
                         name.split("@")[name.split("@").length - 1].length() > 3)) {
                    for (String conference : listOfMUCs.keySet()) {
                        if (!conference.equals(StringUtils.parseBareAddress(participant))) {
                            joinMUC(conference, participant);
                        }
                    }
                }
            } catch (java.io.IOException | SmackException | XMPPException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void left(String participant) {
            participantLeave(participant);
        }
        @Override
        public void kicked(String participant, String s2, String s3) {
            participantLeave(participant);
        }
        @Override
        public void voiceGranted(String s) { }
        @Override
        public void voiceRevoked(String s) { }
        @Override
        public void banned(String participant, String s2, String s3) {
            participantLeave(participant);
        }
        @Override
        public void membershipGranted(String s) { }
        @Override
        public void membershipRevoked(String s) { }
        @Override
        public void moderatorGranted(String s) { }
        @Override
        public void moderatorRevoked(String s) { }
        @Override
        public void ownershipGranted(String s) { }
        @Override
        public void ownershipRevoked(String s) { }
        @Override
        public void adminGranted(String s) { }
        @Override
        public void adminRevoked(String s) { }
        @Override
        public void nicknameChanged(String participant, String newNick) {
/*            try { // TODO: It doesn't work: after quit of user who changed her nick
                changeNick(participant, newNick);
            } catch (SmackException.NotConnectedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
                e.printStackTrace();
            }
*/
            try {
                leaveMUC(participant);
                joinMUC(StringUtils.parseBareAddress(participant), newNick);
            } catch (XMPPException | IOException | SmackException e) {
                e.printStackTrace();
            }
        }
    }

    private static String formatNick(String user) {
        StringBuilder domain = new StringBuilder();
        for (String dom : StringUtils.parseBareAddress(user).split("\\.")) {
            domain.append(dom.charAt(0));
        }
        return StringUtils.parseResource(user)+ "@" + domain.toString();
    }

    private static String formatNick4MUC(String roomNick) {
        String localNick;
        if (roomNick.contains("/")) {
            localNick = formatNick(roomNick);
        } else {
            localNick = roomNick;
        }
        return localNick;
    }
}
