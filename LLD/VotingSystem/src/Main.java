import service.ElectionService;

import java.time.LocalDateTime;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        ElectionService service = new ElectionService();

        // Create election
        service.createElection("E1", 
                LocalDateTime.now().minusHours(1), 
                LocalDateTime.now().plusHours(2), 
                Set.of("12345", "67890"));

        // Create voters
        service.createVoter("V1", "12345");
        service.createVoter("V2", "12345");
        service.createVoter("V3", "67890");

        // Create candidates
        service.createCandidate("V4", "C1", "12345");
        service.createCandidate("V5", "C2", "67890");

        // Add candidates to election
        service.addCandidateInElection("E1", "C1");
        service.addCandidateInElection("E1", "C2");

        // Cast votes
        service.castvote("V1", "C1", "E1");
        service.castvote("V2", "C1", "E1");
        service.castvote("V3", "C2", "E1");

        System.out.println("Voting completed!");
    }
}