package com.agentforge.testinfra.simulation

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

/**
 * Gatling load simulation: full AgentForge business suite (mixed workload).
 *
 * <p>Simulates realistic end-user traffic mix across 3 scenarios:
 * <ul>
 *   <li>70% — List/query published agents (read-heavy, cacheable)</li>
 *   <li>20% — Create chat session + push 3 messages (write + SSE)</li>
 *   <li>10% — Create task for orchestration (heavy backend pipeline)</li>
 * </ul></p>
 *
 * <p>Target: agent-gateway HTTP REST API (port 8080).</p>
 *
 * <p>Run via:
 * <pre>mvn -Pe2e-perf -pl agent-test-infra gatling:test
 *     -Dgatling.simulationClass=com.agentforge.testinfra.simulation.AgentForgeFullSuiteSimulation</pre>
 *
 * <p>Baseline targets (test-plan.md §2.6 — agent concurrency ≥200/instance):
 * <ul>
 *   <li>Global P95 ≤ 1000 ms</li>
 *   <li>Success rate ≥ 95%</li>
 *   <li>Sustained throughput ≥ 100 req/s</li>
 *   <li>Peak concurrency ≥ 200 virtual users</li>
 * </ul></p>
 */
class AgentForgeFullSuiteSimulation extends Simulation {

  val baseUrl = System.getProperty("target.baseUrl", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("AgentForge-Gatling-FullSuite/1.0")
    .acceptEncodingHeader("gzip, deflate")
    .disableWarmUp

  // Scenario 1 (70%): list/query published agents — read-heavy, cacheable.
  val browseAgentsScenario = scenario("Browse Agents")
    .exec(
      http("GET /api/v1/agents?status=PUBLISHED")
        .get("/api/v1/agents?status=PUBLISHED&pageSize=20")
        .check(status.is(200))
    )
    .pause(1, 3)  // user reading the list
    .exec(
      http("GET /api/v1/agents?tier=STANDARD")
        .get("/api/v1/agents?tier=STANDARD&pageSize=10")
        .check(status.in(200, 404))
    )

  // Scenario 2 (20%): create session + push messages — write + SSE push.
  val sessionFeeder = Iterator.continually(Map(
    "sessionId" -> ("gatling-session-" + System.currentTimeMillis() + "-" + Random.nextInt(Int.MaxValue)),
    "userId"    -> ("user-" + Random.nextInt(10000)),
    "msg1"      -> "Hello, can you help me analyze the latest AI trends?",
    "msg2"      -> "What are the key market players in the LLM space?",
    "msg3"      -> "Summarize the top 3 risks for enterprise adoption."
  ))

  val chatSessionScenario = scenario("Chat Session")
    .feed(sessionFeeder)
    .exec(
      http("POST /api/v1/sessions")
        .post("/api/v1/sessions")
        .body(StringBody(
          """{"sessionId":"#{sessionId}","userId":"#{userId}","tenantId":"tn_gatling","status":"ACTIVE"}"""
        )).asJson
        .check(status.in(200, 201))
    )
    .exec(
      http("POST /api/v1/sessions/{id}/messages (1)")
        .post("/api/v1/sessions/#{sessionId}/messages")
        .body(StringBody("""{"role":"USER","content":"#{msg1}"}""")).asJson
        .check(status.in(200, 201, 202))
    )
    .pause(2)
    .exec(
      http("POST /api/v1/sessions/{id}/messages (2)")
        .post("/api/v1/sessions/#{sessionId}/messages")
        .body(StringBody("""{"role":"USER","content":"#{msg2}"}""")).asJson
        .check(status.in(200, 201, 202))
    )
    .pause(2)
    .exec(
      http("POST /api/v1/sessions/{id}/messages (3)")
        .post("/api/v1/sessions/#{sessionId}/messages")
        .body(StringBody("""{"role":"USER","content":"#{msg3}"}""")).asJson
        .check(status.in(200, 201, 202))
    )

  // Scenario 3 (10%): create task for orchestration — heavy backend pipeline.
  val taskFeeder = Iterator.continually(Map(
    "taskId" -> ("suite-task-" + System.currentTimeMillis() + "-" + Random.nextInt(Int.MaxValue)),
    "userId" -> ("user-" + Random.nextInt(10000))
  ))

  val createTaskScenario = scenario("Create Task")
    .feed(taskFeeder)
    .exec(
      http("POST /api/v1/tasks")
        .post("/api/v1/tasks")
        .body(StringBody(
          """{"taskId":"#{taskId}","userId":"#{userId}","tier":"L2","userInput":"Research and report on AI agent platform market","maxSteps":20}"""
        )).asJson
        .check(status.in(200, 201, 202))
    )

  // Mixed workload: 70 / 20 / 10 split. Total peak concurrency = 200 users.
  // rampUsersPerSec ramps each scenario's injection rate; closed-model via atOnceUsers
  // would saturate connection pool, so we use open-model injection.
  setUp(
    browseAgentsScenario.inject(
      rampUsersPerSec(10).to(140).during(60.seconds),  // 70% of 200 peak
      constantUsersPerSec(140).during(180.seconds)
    ),
    chatSessionScenario.inject(
      rampUsersPerSec(2).to(40).during(60.seconds),    // 20% of 200 peak
      constantUsersPerSec(40).during(180.seconds)
    ),
    createTaskScenario.inject(
      rampUsersPerSec(1).to(20).during(60.seconds),    // 10% of 200 peak
      constantUsersPerSec(20).during(180.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(1000),  // P95 ≤ 1s
      global.responseTime.percentile4.lte(3000),  // P99 ≤ 3s
      global.successfulRequests.percent.gte(95),
      global.requestsPerSec.gte(100)
    )
}
