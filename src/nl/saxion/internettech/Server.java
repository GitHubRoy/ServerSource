package nl.saxion.internettech;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static nl.saxion.internettech.ServerState.*;

public class Server {

    private ServerSocket serverSocket;
    private Set<ClientThread> threads;
    private ArrayList<UserGroup> groups;
    private ServerConfiguration conf;


    public Server(ServerConfiguration conf) {
        this.conf = conf;
    }

    /**
     * Runs the server. The server listens for incoming client connections
     * by opening a socket on a specific port.
     */
    public void run() {
        // Create a socket to wait for clients.
        try {
            serverSocket = new ServerSocket(conf.SERVER_PORT);
            threads = new HashSet<>();
            groups = new ArrayList<>();

            while (true) {
                // Wait for an incoming client-connection request (blocking).
                Socket socket = serverSocket.accept();

                // When a new connection has been established, start a new thread.
                ClientThread ct = new ClientThread(socket);
                threads.add(ct);
                new Thread(ct).start();
                System.out.println("Num clients: " + threads.size());

                // Simulate lost connections if configured.
                if (conf.doSimulateConnectionLost()) {
                    DropClientThread dct = new DropClientThread(ct);
                    new Thread(dct).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This thread sleeps for somewhere between 10 tot 20 seconds and then drops the
     * client thread. This is done to simulate a lost in connection.
     */
    private class DropClientThread implements Runnable {
        ClientThread ct;

        DropClientThread(ClientThread ct) {
            this.ct = ct;
        }

        public void run() {
            try {
                // Drop a client thread between 10 to 20 seconds.
                int sleep = (10 + new Random().nextInt(10)) * 1000;
                Thread.sleep(sleep);
                ct.kill();
                threads.remove(ct);
                System.out.println("Num clients: " + threads.size());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This inner class is used to handle all communication between the server and a
     * specific client.
     */
    public class ClientThread implements Runnable {

        private DataInputStream is;
        private OutputStream os;
        private Socket socket;
        private ServerState state;
        private String username;
        private Set<UserGroup> joinedUserGroups = new HashSet<>();

        public ClientThread(Socket socket) {
            this.state = INIT;
            this.socket = socket;
        }

        public String getUsername() {
            return username;
        }

        public OutputStream getOutputStream() {
            return os;
        }

        public void run() {
            try {
                // Create input and output streams for the socket.
                os = socket.getOutputStream();
                is = new DataInputStream(socket.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                // According to the protocol we should send HELO <welcome message>
                state = CONNECTING;
                String welcomeMessage = "HELO " + conf.WELCOME_MESSAGE;
                writeToClient(welcomeMessage);

                while (!state.equals(FINISHED)) {
                    // Wait for message from the client.
                    String line = reader.readLine();
                    if (line != null) {
                        // Log incoming message for debug purposes.
                        boolean isIncomingMessage = true;
                        logMessage(isIncomingMessage, line);

                        // Parse incoming message.
                        Message message = new Message(line);

                        // Process message.
                        switch (message.getMessageType()) {
                            case HELO:
                                // Check username format.
                                boolean isValidUsername = message.getPayload().matches("[a-zA-Z0-9_]{3,14}");
                                if (!isValidUsername) {
                                    state = FINISHED;
                                    writeToClient("-ERR username has an invalid format (only characters, numbers and underscores are allowed)");
                                } else {
                                    // Check if user already exists.
                                    boolean userExists = false;
                                    for (ClientThread ct : threads) {
                                        if (ct != this && message.getPayload().equals(ct.getUsername())) {
                                            userExists = true;
                                            break;
                                        }
                                    }
                                    if (userExists) {
                                        writeToClient("-ERR user already logged in");
                                    } else {
                                        state = CONNECTED;
                                        this.username = message.getPayload();
                                        writeToClient("+OK " + getUsername());
                                    }
                                }
                                break;
                            case BCST:
                                // Broadcast to other clients.
                                for (ClientThread ct : threads) {
                                    if (ct != this) {
                                        ct.writeToClient("BCST [" + getUsername() + "] " + message.getPayload());
                                    }
                                }
                                writeToClient("+OK");
                                break;
                            case LSTUS:
                                //list users
                                System.out.println("[Listing users....]");
                                StringBuilder userlistSB = new StringBuilder();
                                boolean addComma = false;
                                for (ClientThread ct : threads) {
                                    if (addComma) {
                                        userlistSB.append(",");
                                    }
                                    userlistSB.append(ct.getUsername());
                                    addComma = true;
                                }
                                String userlist = userlistSB.toString();
                                writeToClient("+OK [" + userlist + "]");
                                break;
                            case MSG:
                                String returnMessage = "";
                                String[] splits = message.getPayload().split(" ");
                                String recievingUser = splits[0];

                                String whisperMessage = message.getPayload().substring(recievingUser.length() + 1);

                                boolean succes = false;
                                if (threads.size() > 1) {
                                    for (ClientThread ct : threads) {
                                        if (ct.getUsername().equals(recievingUser)) {
                                            ct.writeToClient("WHISPER " + ct.getUsername() + " " + whisperMessage);
                                            succes = true;
                                        }
                                    }
                                }
                                if (!succes) {
                                    writeToClient("-ERR Username doesn't exist.");
                                } else {
                                    writeToClient("+OK");
                                }
                                break;
                            case MKGRP:
                                String[] parse = message.getPayload().split(" ");
                                String groupname = parse[0];
                                if (groupExists(groupname) == null) {
                                    UserGroup group = new UserGroup(groupname, this);
                                    groups.add(group);
                                    joinedUserGroups.add(group);
                                    writeToClient("+OK");
                                } else {
                                    writeToClient("-ERR groupname already exists");
                                }
                                break;
                            case JNGRP:
                                if (groupExists(message.getPayload()) == null) {
                                    writeToClient("-ERR Group doesn't exist.");
                                } else {
                                    for (UserGroup group : groups) {
                                        if (group.getGroupname().equals(message.getPayload())) {
                                            if (joinedUserGroups.contains(group)) {
                                                writeToClient("-ERR already joined this group.");
                                            } else {
                                                group.addParticipant(this);
                                                joinedUserGroups.add(group);
                                                writeToClient("+OK");
                                                group.broadcastGroupMessage(getUsername() + " joined Group", this);
                                                break;
                                            }
                                        }
                                    }
                                }
                                break;
                            case LSTGRP:
                                System.out.println("[Listing groups....]");
                                StringBuilder grouplistSB = new StringBuilder();
                                for (int i = 0; i < groups.size(); i++) {
                                    grouplistSB.append(groups.get(i).getGroupname() + "; ");
                                }
                                String grouplist = grouplistSB.toString();
                                writeToClient("+OK Groups: " + grouplist + "");
                                break;
                            case BCGRP:
                                String groupName = message.getPayload().split(" ")[0];
                                String groupMessage = message.getPayload().substring(groupName.length() + 1);

                                UserGroup grpToBroadcast = groupExists(groupName);
                                if (grpToBroadcast != null && joinedUserGroups.contains(grpToBroadcast)) {
                                    grpToBroadcast.broadcastGroupMessage("[" + getUsername() + "]" + groupMessage, this);
                                    writeToClient("+OK");
                                } else {
                                    writeToClient("-ERR not in this group");
                                }
                                break;
                            case LVGRP:
                                String groupToLeave = message.getPayload().trim();

                                UserGroup grpToLeave = groupExists(groupToLeave);
                                if (grpToLeave != null && joinedUserGroups.contains(grpToLeave)) {
                                    joinedUserGroups.remove(grpToLeave);
                                    boolean isgroupowner = grpToLeave.removeParticipant(this);
                                    writeToClient("+OK");
                                    if (!isgroupowner) {
                                        grpToLeave.broadcastGroupMessage(getUsername() + " left the group", this);
                                    } else {
                                        grpToLeave.disbandGroup();
                                    }
                                } else {
                                    writeToClient("-ERR not in this group");
                                }
                                break;
                            case KICK:
                                groupName = message.getPayload().split(" ")[0];
                                String userToKick = message.getPayload().substring(groupName.length() + 1);

                                System.out.println(userToKick);

                                UserGroup currentGroup = groupExists(groupName);
                                if (currentGroup != null && currentGroup.getGroupowner() == this) {
                                    ClientThread user = currentGroup.getParticipant(userToKick);
                                    if (user != null) {
                                        if (user != this) {
                                            user.joinedUserGroups.remove(currentGroup);
                                            currentGroup.removeParticipant(user);
                                            user.writeToClient("+OK kicked From group [" + currentGroup.getGroupname() + "]");
                                            writeToClient("+OK");
                                        } else {
                                            writeToClient("-ERR You cannot kick yourself");
                                        }
                                    } else {
                                        writeToClient("-ERR User is not in this group");
                                    }
                                } else {
                                    writeToClient("-ERR You are not the owner");
                                }
                                break;
                            case TRNSFR:
                                String receivinguser = message.getPayload().split(" ")[0];
                                if (receivinguser != null && fileTransfer(receivinguser)) {
                                    writeToClient("+OK");
                                } else {
                                    writeToClient("-ERR Failed to receive file");
                                }

                                break;
                            case QUIT:
                                // Close connection
                                state = FINISHED;
                                writeToClient("+OK Goodbye");
                                break;
                            case UNKOWN:
                                // Unkown command has been sent
                                writeToClient("-ERR Unkown command");
                                break;
                        }
                    }
                }
                // Remove from the list of client threads and close the socket.
                threads.remove(this);
                socket.close();
            } catch (
                    IOException e)

            {
                System.out.println("Server Exception: " + e.getMessage());
            }
        }

        /**
         * An external process can stop the client using this methode.
         */
        public void kill() {
            try {
                // Log connection drop and close the outputstream.
                System.out.println("[DROP CONNECTION] " + getUsername());
                threads.remove(this);
                socket.close();
            } catch (Exception ex) {
                System.out.println("Exception when closing outputstream: " + ex.getMessage());
            }
            state = FINISHED;
        }

        /**
         * Write a message to this client thread.
         *
         * @param message The message to be sent to the (connected) client.
         */
        public void writeToClient(String message) {
            boolean shouldDropPacket = false;
            boolean shouldCorruptPacket = false;

            // Check if we need to behave badly by dropping some messages.
            if (conf.doSimulateDroppedPackets()) {
                // Randomly select if we are going to drop this message or not.
                int random = new Random().nextInt(6);
                if (random == 0) {
                    // Drop message.
                    shouldDropPacket = true;
                    System.out.println("[DROPPED] " + message);
                }
            }

            // Check if we need to behave badly by corrupting some messages.
            if (conf.doSimulateCorruptedPackets()) {
                // Randomly select if we are going to corrupt this message or not.
                int random = new Random().nextInt(4);
                if (random == 0) {
                    // Corrupt message.
                    shouldCorruptPacket = true;
                }
            }

            // Do the actual message sending here.
            if (!shouldDropPacket) {
                if (shouldCorruptPacket) {
                    message = corrupt(message);
                    System.out.println("[CORRUPT] " + message);
                }
                PrintWriter writer = new PrintWriter(os);
                writer.println(message);
                writer.flush();

                // Echo the message to the server console for debugging purposes.
                boolean isIncomingMessage = false;
                logMessage(isIncomingMessage, message);
            }
        }

        /**
         * This methods implements a (naive) simulation of a corrupt message by replacing
         * some charaters at random indexes with the charater X.
         *
         * @param message The message to be corrupted.
         * @return Returns the message with some charaters replaced with X's.
         */
        private String corrupt(String message) {
            Random random = new Random();
            int x = random.nextInt(4);
            char[] messageChars = message.toCharArray();

            while (x < messageChars.length) {
                messageChars[x] = 'X';
                x = x + random.nextInt(10);
            }

            return new String(messageChars);
        }

        /**
         * Util method to print (debug) information about the server's incoming and outgoing messages.
         *
         * @param isIncoming Indicates whether the message was an incoming message. If false then
         *                   an outgoing message is assumed.
         * @param message    The message received or sent.
         */
        private void logMessage(boolean isIncoming, String message) {
            String logMessage;
            String colorCode = conf.CLI_COLOR_OUTGOING;
            String directionString = ">> ";  // Outgoing message.
            if (isIncoming) {
                colorCode = conf.CLI_COLOR_INCOMING;
                directionString = "<< ";     // Incoming message.
            }

            // Add username to log if present.
            // Note when setting up the connection the user is not known.
            if (getUsername() == null) {
                logMessage = directionString + message;
            } else {
                logMessage = directionString + "[" + getUsername() + "] " + message;
            }

            // Log debug messages with or without colors.
            if (conf.isShowColors()) {
                System.out.println(colorCode + logMessage + conf.RESET_CLI_COLORS);
            } else {
                System.out.println(logMessage);
            }
        }


        private UserGroup groupExists(String groupname) {
            if (groups.isEmpty()) {
                return null;
            }
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).getGroupname().equals(groupname)) {
                    return groups.get(i);
                }
            }
            return null;
        }

        public void removeGroupFromJoinedGroups(UserGroup group) {
            joinedUserGroups.remove(group);
        }

        private boolean fileTransfer(String receivingUser) throws IOException {
            byte[] buffer = new byte[4096];

            is.read(buffer, 0, buffer.length);
            String file = new String(buffer).trim();

            is.read(buffer, 0, buffer.length);
            int filesize = Integer.parseInt(new String(buffer).trim());

            FileOutputStream fos = new FileOutputStream(file);
            int read = 0;
            int totalRead = 0;
            int remaining = filesize;
            while ((read = is.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                totalRead += read;
                remaining -= read;
                System.out.println("read " + totalRead + " bytes.");
                fos.write(buffer, 0, read);
            }
            if (totalRead == filesize) {
                try {
                    File f = new File(file);
                    sendFile(f, receivingUser);

                    fos.close();
                    return true;
                } catch (IOException io) {
                    fos.close();
                    return false;
                }
            } else {
                writeToClient("-ERR Failed to receive file");
                fos.close();
                return false;
            }
        }

        private void sendFile(File file, String receivinguser) throws IOException {
            boolean succes = false;
            OutputStream receiver = null;
            ClientThread recipient = null;
            for (ClientThread ct : threads) {
                if (ct.getUsername().equals(receivinguser)) {
                    ct.writeToClient("TRNSFR from " + username);
                    recipient = ct;
                    receiver = ct.getOutputStream();
                    succes = true;
                }
            }
            if (succes && receiver != null) {
                FileInputStream fis = new FileInputStream(file);
                DataOutputStream dos = new DataOutputStream(receiver);
                byte[] buffer = new byte[4096];

                byte[] name = file.getName().getBytes();
                name = Arrays.copyOf(name, 4096);
                dos.write(name);

                byte[] size = (file.length() + "").getBytes();
                size = Arrays.copyOf(size, 4096);
                dos.write(size);

                while (fis.read(buffer) > 0) {
                    dos.write(buffer);
                }
                recipient.writeToClient("+OK");
                fis.close();
            }
        }
    }
}
