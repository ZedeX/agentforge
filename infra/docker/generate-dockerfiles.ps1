# Generate 12 per-service Dockerfiles from template.
# Each Dockerfile: FROM base + SW_AGENT_NAME + EXPOSE + HEALTHCHECK + VOLUME.
# Run: pwsh -File generate-dockerfiles.ps1

$services = @(
    @{name="agent-gateway"; http=8080; grpc=$null},
    @{name="agent-session"; http=8082; grpc=$null},
    @{name="agent-task-orchestrator"; http=8084; grpc=9090},
    @{name="agent-memory"; http=8088; grpc=9088},
    @{name="agent-tool-engine"; http=8090; grpc=9090},
    @{name="agent-runtime"; http=8092; grpc=9092},
    @{name="agent-model-gateway"; http=8094; grpc=9094},
    @{name="agent-repo"; http=8096; grpc=9096},
    @{name="agent-knowledge"; http=8098; grpc=9098},
    @{name="agent-quality"; http=8100; grpc=9100},
    @{name="agent-risk-control"; http=8102; grpc=9102},
    @{name="agent-observability"; http=8104; grpc=$null}
)

$dir = Split-Path -Parent $MyInvocation.MyCommand.Path

foreach ($svc in $services) {
    $expose = "EXPOSE $($svc.http)"
    if ($svc.grpc) { $expose += " $($svc.grpc)" }
    $content = @"
# $($svc.name) service Dockerfile.
# Build: docker build -t agentplatform/$($svc.name):1.0.0 -f Dockerfile.$($svc.name) --build-arg SERVICE_NAME=$($svc.name) -f Dockerfile.template .

FROM agentplatform/base:17-jre-skywalking-9.7

LABEL service="$($svc.name)"
LABEL tier="service"

ENV SW_AGENT_NAME="agent-platform-$($svc.name)"
ENV SW_COLLECTOR_BACKEND_SERVICE="skywalking-oap.agent-platform-infra:11800"

$expose

VOLUME ["/var/log/agent-platform"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:$($svc.http)/actuator/health || exit 1
"@
    $path = Join-Path $dir "Dockerfile.$($svc.name)"
    Set-Content -Path $path -Value $content -Encoding UTF8 -NoNewline
    Write-Host "Generated: $path"
}
