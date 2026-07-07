package com.agent.observability.model;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for GetHealth request.
 */
public class GetHealthRequest {

    private List<String> serviceNames = new ArrayList<>();

    public GetHealthRequest() {
    }

    public GetHealthRequest(List<String> serviceNames) {
        this.serviceNames = serviceNames != null ? serviceNames : new ArrayList<>();
    }

    public List<String> getServiceNames() {
        return serviceNames;
    }

    public void setServiceNames(List<String> serviceNames) {
        this.serviceNames = serviceNames;
    }
}
