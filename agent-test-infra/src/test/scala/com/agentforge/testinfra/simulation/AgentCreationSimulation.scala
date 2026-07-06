package com.agentforge.testinfra.simulation

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

/**
 * Gatling load simulation: Agent creation endpoint (POST /api/v1/agents).
 *
 * <p>Target: agent-gateway HTTP REST API (port 8080 by default).
 * Validates throughput + latency of the agent-repo write path.</p>
 *
 * <p>Run via:
 * <pre>mvn -Pe2e-perf -pl agent-test-infra gatling:test
 *     -Dgatling.simulationClass=com.agentforge.testinfra.simulation.AgentCreationSimulation</pre>
 *
 * <p>Override target URL via System property:
 * <pre>-Dtarget.baseUrl=http://staging-agent-gateway:8080</pre></p>
 *
 * <p>Baseline targets (test-plan.md §2.6):
 * <ul>
 *   <li>P95 latency ≤ 200 ms</li>
 *   <li>Success rate ≥ 99%</li>
 *   <li>Throughput ≥ 50 req/s sustained</li>
 * </ul></p>
 */
class AgentCreationSimulation extends Simulation {

  val baseUrl = System.getProperty("target.baseUrl", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("AgentForge-Gatling/1.0")
    .acceptEncodingHeader("gzip, deflate")

  // Generate unique agentId per virtual user to exercise uk_agent_id constraint path.
  val feeder = Iterator.continually(Map(
    "agentId" -> ("gatling-agent-" + System.currentTimeMillis() + "-" + Random.nextInt(Int.MaxValue)),
    "name"    -> ("Load Test Agent " + Random.nextInt(10000)),
    "tier"    -> (if (Random.nextInt(3) == 0) "ADVANCED" else if (Random.nextInt(2) == 0) "STANDARD" else "LITE")
  ))

  val createAgentScenario = scenario("Create Agent")
    .feed(feeder)
    .exec(
      http("POST /api/v1/agents")
        .post("/api/v1/agents")
        .body(StringBody(
          """{"agentId":"#{agentId}","name":"#{name}","description":"gatling load test","systemPrompt":"you are a test agent","agentTier":"#{tier}","maxSteps":10,"maxToken":4096}"""
        )).asJson
        .check(status.is(201))
        .check(jsonPath("$.agentId").saveAs("responseAgentId"))
    )
    // Cleanup: delete the agent to keep DB clean across reruns.
    .exec(
      http("DELETE /api/v1/agents/{id}")
        .delete("/api/v1/agents/#{responseAgentId}")
        .check(status.in(200, 204, 404))
    )

  // Injection profile: ramp to 50 users over 30s, hold 50 users for 60s.
  setUp(
    createAgentScenario.inject(
      rampUsersPerSec(1).to(50).during(30.seconds),
      constantUsersPerSec(50).during(60.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.mean.lte(200),
      global.responseTime.percentile3.lte(500),  // P95
      global.successfulRequests.percent.gte(95),
      global.requestsPerSec.gte(40)
    )
}
