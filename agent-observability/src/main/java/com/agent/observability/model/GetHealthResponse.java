package com.agent.observability.model;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for GetHealth response.
 */
public class GetHealthResponse {

    private String overallStatus;
    private List<ServiceHealth> services = new ArrayList<>();

    public GetHealthResponse() {
    }

    public GetHealthResponse(String overallStatus, List<ServiceHealth> services) {
        this.overallStatus = overallStatus;
        this.services = services != null ? services : new ArrayList<>();
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public List<ServiceHealth> getServices() {
        return services;
    }

    public void setServices(List<ServiceHealth> services) {
        this.services = services;
    }
}
