package nl.saxion.internettech;

import java.util.ArrayList;


public class UserGroup {

    private String groupname;
    private Server.ClientThread groupowner;
    private ArrayList<Server.ClientThread> participants = new ArrayList<>();

    public UserGroup(String groupname, Server.ClientThread groupowner){
        this.groupname = groupname;
        this.groupowner = groupowner;
    }

    public String getGroupname() {
        return groupname;
    }

    public Server.ClientThread getGroupowner() {
        return groupowner;
    }

    public boolean Participates(String username) {
        if (participants.isEmpty()){
            return false;
        }
        for (int i = 0; i < participants.size(); i++){
            if(participants.get(i).getUsername().equals(username)){
                return true;
            }
        }
        return false;
    }

    public boolean addParticipant(Server.ClientThread participant) {
        participants.add(participant);
        return true;
    }

    public ArrayList<Server.ClientThread> getParticipants() {
        return participants;
    }
}
