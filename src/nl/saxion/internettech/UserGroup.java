package nl.saxion.internettech;

import com.sun.deploy.util.SessionState;

import java.util.ArrayList;


public class UserGroup {

    private String groupname;
    private Server.ClientThread groupowner;
    private ArrayList<Server.ClientThread> participants = new ArrayList<>();

    public UserGroup(String groupname, Server.ClientThread groupowner) {
        this.groupname = groupname;
        this.groupowner = groupowner;
        participants.add(groupowner);
    }

    public String getGroupname() {
        return groupname;
    }

    public Server.ClientThread getGroupowner() {
        return groupowner;
    }

    public boolean Participates(String username) {
        if (participants.isEmpty()) {
            return false;
        }
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public boolean addParticipant(Server.ClientThread participant) {
        participants.add(participant);
        return true;
    }

    public boolean removeParticipant(Server.ClientThread participant) {
        if (groupowner.getUsername().equals(participant.getUsername())) {
            return true;
        }
        int indexToRemove = -1;
        for (int i = 0; i < participants.size(); i++) {
            Server.ClientThread ct = participants.get(i);
            if (ct.getUsername().equals(participant.getUsername())) {
                indexToRemove = i;
                break;
            }
        }
        if (indexToRemove != -1) {
            participants.remove(indexToRemove);
        }
        return false;
    }

    public ArrayList<Server.ClientThread> getParticipants() {
        return participants;
    }

    public void broadcastGroupMessage(String message, Server.ClientThread sender) {
        for (Server.ClientThread ct : participants) {
            if (ct != sender) {
                ct.writeToClient("BCST [" + groupname + "] " + message);
            }
        }
    }

    public void disbandGroup() {
        for (Server.ClientThread ct : participants) {
            if (ct != groupowner) {
                ct.removeGroupFromJoinedGroups(this);
            }
        }
        broadcastGroupMessage("Disbanded", groupowner);
    }

    public Server.ClientThread getParticipant(String username) {
        for (Server.ClientThread ct : participants) {
            if (username.equals(ct.getUsername())) {
                return ct;
            }
        }
        return null;
    }

}
