package models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Voter {
    private final String voterId;
    private final String pinCode;
}
