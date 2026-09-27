package com.sourcesignal.dto;

import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.NeedType;
import com.sourcesignal.enums.Region;
import lombok.Data;

@Data
public class LeadQueryRequest {
    private LeadGrade grade;
    private String category;
    private Region region;
    private NeedType needType;
    private String keyword;
    private Boolean marked;
    private Boolean unread;
    private Integer page = 1;
    private Integer size = 20;
}
