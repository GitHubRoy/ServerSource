package nl.saxion.internettech;

import java.util.ArrayList;


public class UserGroup {

    private String groupname;
    private String groupowner;
    private ArrayList<String> participants = new ArrayList<>();

    public UserGroup(String groupname, String groupowner){
        this.groupname = groupname;
        this.groupowner = groupowner;
    }

    public String getGroupname() {
        return groupname;
    }

    public String getGroupowner() {
        return groupowner;
    }

    public boolean Participates(String username) {
        if (participants.isEmpty()){
            return false;
        }
        for (int i = 0; i < participants.size(); i++){
            if(participants.get(i).equals(username)){
                return true;
            }
        }
        return false;
    }

    public boolean setParticipants(String participant) {
        participants.add(participant);
        return true;
    }
}
