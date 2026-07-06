package com.agentforge.testinfra.container;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.RedisContainer;

/**
 * Shared Testcontainers factory for e2e + perf tests (docs/tests/test-strategy.md §4.2).
 *
 * <p>Centralizes container image versions, network setup, and common configuration
 * so that per-module {@code *TestcontainersTest} classes and Gatling simulations
 * reference a single source of truth. Version bumps happen here, not in 5+ modules.</p>
 *
 * <p>Usage:
 * <pre>
 *   MySQLContainer<?> mysql = ContainerFactory.sharedMySQL("agent_repo");
 *   RedisContainer   redis = ContainerFactory.sharedRedis();
 * </pre>
 *
 * <p>All containers share a {@link Network} so they can resolve each other by alias
 * (useful for multi-container e2e scenarios like Milvus + etcd + minio).</p>
 */
public final class ContainerFactory {

    /** Image versions — single source of truth (test-plan.md §6.1). */
    public static final String MYSQL_IMAGE = "mysql:8.0.36";
    public static final String REDIS_IMAGE = "redis:7.2-alpine";

    private static final Network SHARED_NETWORK = Network.newNetwork();

    private ContainerFactory() {
        // utility class
    }

    /**
     * Create a MySQL 8.0.36 container with utf8mb4 + utf8mb4_0900_ai_ci.
     *
     * @param dbName target database name (created automatically on first start)
     */
    public static MySQLContainer<?> sharedMySQL(String dbName) {
        return new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                .withNetwork(SHARED_NETWORK)
                .withNetworkAliases("mysql-" + dbName)
                .withDatabaseName(dbName)
                .withUsername("root")
                .withPassword("root")
                .withCommand("--character-set-server=utf8mb4",
                             "--collation-server=utf8mb4_0900_ai_ci");
    }

    /**
     * Create a Redis 7.2-alpine container for cache + session store tests.
     */
    public static RedisContainer sharedRedis() {
        return new RedisContainer(DockerImageName.parse(REDIS_IMAGE))
                .withNetwork(SHARED_NETWORK)
                .withNetworkAliases("redis-shared");
    }

    /**
     * Shared network for multi-container e2e scenarios.
     * Containers on the same network can resolve each other by alias.
     */
    public static Network sharedNetwork() {
        return SHARED_NETWORK;
    }
}
