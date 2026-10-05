package com.mindman.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** A current-turn textual cue with a verifiable quote, never a clinical score. */
@Data
@AllArgsConstructor
public class EmotionCueVO {

    private String label;
    private String status;
    private String evidence;
}
