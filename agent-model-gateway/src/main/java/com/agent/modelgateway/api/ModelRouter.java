package com.agent.modelgateway.api;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.RouteResult;

/**
 * Model router (doc 02-api §3, PRD §二(二)1 routing strategy).
 *
 * <p>Selects primary + fallback provider by scene / cost budget / availability.
 * Skeleton stage: in-memory rule matching. JPA-backed routing deferred to Plan 07 T3.</p>
 */
public interface ModelRouter {

    /**
     * Resolve route for the given scene + preferred model.
     *
     * @param scene          routing scene (intent / audit / generic)
     * @param preferredModel preferred model code, null/empty for default routing
     * @return RouteResult with primary + fallback provider codes
     */
    RouteResult route(Scene scene, String preferredModel);
}
