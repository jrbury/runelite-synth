package com.synth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Event published to the Synth API when a boss kill is recorded
 */
@Data
@AllArgsConstructor
public class BossKill
{
    private String username;
    private String boss_name;
    private Integer count;
}
