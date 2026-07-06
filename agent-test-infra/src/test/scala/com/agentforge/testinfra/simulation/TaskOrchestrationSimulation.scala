package com.agentforge.testinfra.simulation

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

/**
 * Gatling load simulation: Task orchestration endpoint (POST /api/v1/tasks).
 *
 * <p>Target: agent-gateway HTTP REST API → agent-task-orchestrator gRPC.
 * Validates the L1/L2 task planning + DAG scheduling path under load.</p>
 *
 * <p>Run via:
 * <pre>mvn -Pe2e-perf -pl agent-test-infra gatling:test
 *     -Dgatling.simulationClass=com.agentforge.testinfra.simulation.TaskOrchestrationSimulation</pre>
 *
 * <p>Baseline targets (test-plan.md §2.6):
 * <ul>
 *   <li>DAG schedule ≤ 100 ms (P99)</li>
 *   <li>Task create P95 ≤ 500 ms</li>
 *   <li>Success rate ≥ 90% (planning may reject invalid inputs)</li>
 * </ul></p>
 */
class TaskOrchestrationSimulation extends Simulation {

  val baseUrl = System.getProperty("target.baseUrl", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("AgentForge-Gatling/1.0")

  val feeder = Iterator.continually(Map(
    "taskId"    -> ("gatling-task-" + System.currentTimeMillis() + "-" + Random.nextInt(Int.MaxValue)),
    "userId"    -> ("user-" + Random.nextInt(1000)),
    "tier"      -> "L1",
    "userInput" -> ("Analyze market trends in " + List("AI", "fintech", "biotech", "energy", "robotics")(Random.nextInt(5)))
  ))

  val createTaskScenario = scenario("Create and Track Task")
    .feed(feeder)
    .exec(
      http("POST /api/v1/tasks")
        .post("/api/v1/tasks")
        .body(StringBody(
          """{"taskId":"#{taskId}","userId":"#{userId}","tier":"#{tier}","userInput":"#{userInput}","maxSteps":10}"""
        )).asJson
        .check(status.in(200, 201, 202))
        .check(jsonPath("$.taskId").saveAs("createdTaskId"))
    )
    // Poll task status 3 times with 2s gap to simulate client tracking.
    .exec(
      http("GET /api/v1/tasks/{id}")
        .get("/api/v1/tasks/#{createdTaskId}")
        .check(status.in(200, 404))
    )
    .pause(2)
    .exec(
      http("GET /api/v1/tasks/{id} (poll 2)")
        .get("/api/v1/tasks/#{createdTaskId}")
        .check(status.in(200, 404))
    )
    .pause(2)
    .exec(
      http("GET /api/v1/tasks/{id} (poll 3)")
        .get("/api/v1/tasks/#{createdTaskId}")
        .check(status.in(200, 404))
    )

  // Injection profile: ramp from 5 to 20 users/sec over 30s, hold 20 users/sec for 120s.
  // Total ~2400 task creations across the sustained window.
  setUp(
    createTaskScenario.inject(
      rampUsersPerSec(5).to(20).during(30.seconds),
      constantUsersPerSec(20).during(120.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.mean.lte(300),
      global.responseTime.percentile3.lte(800),  // P95
      global.successfulRequests.percent.gte(90),
      global.requestsPerSec.gte(15)
    )
}
