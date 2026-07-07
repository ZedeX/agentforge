package com.agent.riskcontrol.model;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for content safety check request.
 */
public class CheckContentRequest {

    private String content;
    private String contentType;
    private List<String> checkCategories = new ArrayList<>();

    public CheckContentRequest() {
    }

    public CheckContentRequest(String content, String contentType, List<String> checkCategories) {
        this.content = content;
        this.contentType = contentType;
        this.checkCategories = checkCategories != null ? checkCategories : new ArrayList<>();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public List<String> getCheckCategories() {
        return checkCategories;
    }

    public void setCheckCategories(List<String> checkCategories) {
        this.checkCategories = checkCategories;
    }
}
