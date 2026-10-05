package com.mindman.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** An optional AI reference score with a verifiable source quote. */
@Data
@AllArgsConstructor
public class GardenScoreVO {
    private Integer score;
    private String evidence;
    private String reason;
}
