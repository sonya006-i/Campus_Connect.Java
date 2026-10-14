package model;

/**
 * Club — Model representing a university student club or society.
 */
public class Club {

    private int clubId;
    private String clubName;
    private String description;

    public Club() {
    }

    public Club(int clubId, String clubName, String description) {
        this.clubId = clubId;
        this.clubName = clubName;
        this.description = description;
    }

    public Club(String clubName, String description) {
        this.clubName = clubName;
        this.description = description;
    }

    public int getClubId() {
        return clubId;
    }

    public void setClubId(int clubId) {
        this.clubId = clubId;
    }

    public String getClubName() {
        return clubName;
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Club{" +
                "clubId=" + clubId +
                ", clubName='" + clubName + '\'' +
                '}';
    }
}
