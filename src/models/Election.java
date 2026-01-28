package models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public class Election {
    private final String electionId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Set<String> pinCodes;
    private final Map<String, Long> candidateVotesCount;
    private final Map<String, Boolean> voterStatus;


    public Election(List<String> candidates, String electionId, LocalDateTime startTime, LocalDateTime endTime, Set<String> pinCodes) {

        this.electionId = electionId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.pinCodes = pinCodes;
        this.voterStatus = new ConcurrentHashMap<>();
        candidateVotesCount = new ConcurrentHashMap<>();

        for (String candidate : candidates) {
            candidateVotesCount.put(candidate, 0L);
        }
    }

    public Election(String electionId, LocalDateTime startTime, LocalDateTime endTime, Set<String> pinCodes) {

        this.electionId = electionId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.pinCodes = pinCodes;
        this.voterStatus = new ConcurrentHashMap<>();
        candidateVotesCount = new ConcurrentHashMap<>();

    }

    public void addCandidate(String candidateId) {
        if (candidateVotesCount.containsKey(candidateId)) {
            throw new RuntimeException("Candidate already exist with this id");
        }
        candidateVotesCount.put(candidateId, 0L);
    }


    public boolean isElectionRunning() {
        return startTime.isBefore(LocalDateTime.now()) && endTime.isAfter(LocalDateTime.now());
    }

    public boolean isEligibleVoter(Voter voter) {
        return pinCodes.contains(voter.getPinCode()) && !voterStatus.containsKey(voter.getVoterId());
    }

    public void castVote(Voter voter, String candidateId) {
        synchronized (voter) {
            if (isEligibleVoter(voter) && candidateVotesCount.containsKey(candidateId)) {
                candidateVotesCount.put(candidateId, candidateVotesCount.get(candidateId) + 1);
                voterStatus.put(voter.getVoterId(), Boolean.TRUE);
            }
        }
    }

    public List<String> getWinners() {
        long maxVotes = candidateVotesCount.values().stream().max(Long::compare).orElse(0L);
        return candidateVotesCount.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public Long getTotalVoteCast() {
        return candidateVotesCount.values().stream().mapToLong(Long::longValue).sum();
    }

    public void withDrawCandidate(String candidateId){
        candidateVotesCount.remove(candidateId);
    }


}
