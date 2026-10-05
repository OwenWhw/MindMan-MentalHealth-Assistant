package com.mindman.dto;

import lombok.Data;

/** Only the current record is analyzed; reference scores require direct evidence and are not diagnoses. */
@Data
public class GardenInsightVO {
    private String source;
    private String evidence;
    private String observation;
    private String question;
    private GardenScoreVO emotionScore;
    private GardenScoreVO sleepScore;
    private GardenScoreVO stressScore;
}
