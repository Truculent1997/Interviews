package service;

import models.Candidate;
import models.Election;
import models.Voter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ElectionService {

    private final Map<String, Election> electionRegistry;
    private final Map<String, Voter> votersRegistry;
    private final Map<String, Candidate> candidateRegistry;

    public ElectionService() {
        this.votersRegistry = new ConcurrentHashMap<>();
        this.electionRegistry = new ConcurrentHashMap<>();
        this.candidateRegistry = new ConcurrentHashMap<>();
    }

    public void createElection(String electionId, LocalDateTime startTime, LocalDateTime endTime, Set<String> pinCodes) throws RuntimeException {
        if (electionId == null || electionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Election ID cannot be null or empty");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time cannot be null");
        }
        if (pinCodes == null) {
            throw new IllegalArgumentException("Pin codes cannot be null");
        }

        Election election = new Election(electionId, startTime, endTime, pinCodes);
        if (electionRegistry.putIfAbsent(electionId, election) != null) {
            throw new RuntimeException("Election with this id already exist");
        }
    }

    public void addCandidateInElection(String electionId, String candidateId) {
        if (electionId == null || electionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Election ID cannot be null or empty");
        }
        if (candidateId == null || candidateId.trim().isEmpty()) {
            throw new IllegalArgumentException("Candidate ID cannot be null or empty");
        }

        if (!electionRegistry.containsKey(electionId)) {
            throw new RuntimeException("Election with this id doesn't exist");
        }

        Election election = electionRegistry.get(electionId);

        if (election.getStartTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Election is not started yet");
        }

        election.addCandidate(candidateId);
    }

    public void createVoter(String voterId, String pinCode) {
        Voter voter = new Voter(voterId, pinCode);
        if (votersRegistry.putIfAbsent(voterId, voter) != null) {
            throw new RuntimeException("VoterID exist");
        }
    }

    public void createCandidate(String voterId, String candidateId, String pinCode) {
        Candidate candidate = new Candidate(candidateId, voterId, pinCode);
        if (candidateRegistry.putIfAbsent(candidateId, candidate) != null) {
            throw new RuntimeException("Candidate already exist");
        }
    }

    public void castvote(String voterId, String candidateId, String electionId) {
        /*
        All validity check here before
         */

        Election election = electionRegistry.get(electionId);
        Voter voter = votersRegistry.get(voterId);
        election.castVote(voter, candidateId);

    }
}
