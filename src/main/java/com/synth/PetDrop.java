package com.synth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Event published to the Synth API when a pet drop is recorded
 */
@Data
@AllArgsConstructor
public class PetDrop
{
    private String username;
    private String boss_name;
    private Integer kill_count;
}
