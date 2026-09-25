package com.sourcesignal.dto;

import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.NeedType;
import com.sourcesignal.enums.OrderScale;
import com.sourcesignal.enums.Region;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadDTO {
    private Long id;
    private LeadGrade grade;
    private String gradeLabel;
    private String title;
    private String body;
    private String sourceUrl;
    private String author;
    private String subreddit;
    private String category;
    private OrderScale orderScale;
    private NeedType needType;
    private Region region;
    private LocalDateTime postedAt;
    private LocalDateTime pushedAt;
    private Boolean marked;
    private Boolean isRead;
    private String note;
}
