package models;

import lombok.Getter;

@Getter
public class Candidate extends Voter {
    private final String id;
    private final CandidateStatus status;

    public Candidate(String voterId, String pinCode, String id) {
        super(voterId, pinCode);
        this.id = id;
        this.status = CandidateStatus.ACTIVE;
    }
}
