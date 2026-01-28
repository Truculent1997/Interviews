# Multi-Candidate Voting System (LLD)

## 📌 Overview
This repository contains a thread-safe, low-level design (LLD) for a digital voting system. It manages elections, voter eligibility via pincodes, and real-time tallying of results while strictly enforcing the "one-vote-per-person" rule.

## 🏗️ Design & Architecture

### Core Entities
* **Voter**: Base class containing `voterId` and `pinCode`.
* **Candidate**: Extends `Voter` to include `candidateId` and `status` (Active/Withdrawn).
* **Election**: The aggregate root that encapsulates the voting logic, time constraints, and the vote maps.

### Key Technical Decisions
* **Concurrency**: Uses `ConcurrentHashMap` for all registries and vote tallies to handle thousands of simultaneous requests without data corruption.
* **Atomic Operations**: Employs `putIfAbsent` for voter status to prevent "double-voting" race conditions without the heavy overhead of global locks.
* **Domain Logic**: The `Election` class handles its own state (start/end times), ensuring that the `ElectionService` remains a thin orchestration layer.

---

## 🚀 Features
* **Regional Eligibility**: Voters can only participate in elections matching their `pincode`.
* **Temporal Constraints**: Votes are only accepted between the `startTime` and `endTime` of an election.
* **Real-time Leaderboard**: Calculate the current leader or the final winner with $O(N)$ complexity where $N$ is the number of candidates.
* **Candidate Management**: Supports adding candidates before an election begins and tracking their active status.

---

## 🛠️ Class Structure

```mermaid
classDiagram
    class Voter {
        +String voterId
        +String pinCode
    }
    class Candidate {
        +String candidateId
        +CandidateStatus status
    }
    class Election {
        +String electionId
        +LocalDateTime startTime
        +LocalDateTime endTime
        +Set~String~ pinCodes
        +Map~String, Long~ candidateVotesCount
        +Map~String, Boolean~ voterStatus
        +castVote(Voter, String)
        +isElectionRunning()
    }
    Voter <|-- Candidate
    Election "1" -- "*" Candidate




---

## ❓ Frequently Asked Questions (Interview Prep)

### 1. How does the system prevent a user from voting twice?
The system utilizes the `putIfAbsent` method on a `ConcurrentHashMap` (the `voterStatus` map). This is an atomic "check-and-set" operation. If the voter ID already exists, the method returns the existing value, allowing the system to reject the second attempt immediately before any vote is tallied.

### 2. Is this system thread-safe?
Yes. By using `ConcurrentHashMap` for both voter tracking and candidate tallies, we avoid `ConcurrentModificationException` and race conditions during high-concurrency periods. For updating the vote count, we use `computeIfPresent`, which ensures the increment operation is atomic.

### 3. Why did you use Inheritance for the Candidate class?
A `Candidate` is fundamentally a `Voter` who has been granted additional attributes (like a `CandidateID` and `Status`). Using inheritance allows the `Election` logic to treat both entities uniformly when verifying identities or pincodes, effectively following the **Liskov Substitution Principle**.

### 4. How would you handle a tie between two candidates?
The current `mostVotes()` implementation uses a stream to find the maximum value. In a production scenario, I would modify this logic to return a `List<Candidate>` containing all candidates who share the highest vote count, rather than just the first one found.

### 5. What happens if the service crashes? 
Since the current design is in-memory, data would be lost on a crash. In a production environment, I would persist every vote to a Relational Database with a `UNIQUE` constraint on the tuple `(voter_id, election_id)` to ensure ACID compliance and data durability.

### 6. How would you scale this to millions of voters?
To scale horizontally across multiple servers, I would:
* **Distributed Locking**: Move from JVM-local locks to a distributed lock manager like **Redis** or **Zookeeper**.
* **Database Sharding**: Partition the voting data by `electionId` to distribute the load.
* **Asynchronous Processing**: Use a message queue (e.g., Kafka) to buffer incoming votes if the database write-throughput becomes a bottleneck.