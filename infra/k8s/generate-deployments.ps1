# Generate 12 Deployment + 12 Service + 1 Ingress yaml files.
# Based on doc 09 §13.2 Deployment template + §14.3 resource quota table.
# Run: pwsh -File generate-deployments.ps1

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$depDir = Join-Path $scriptDir "deployments"
$svcDir = Join-Path $scriptDir "services"

# Helper: convert "1Gi" -> 1024 (MB for -Xmx)
function Convert-MemToMB([string]$mem) {
    if ($mem -match '^(\d+)Gi$') { return [int]$Matches[1] * 1024 }
    if ($mem -match '^(\d+)Mi$') { return [int]$Matches[1] }
    return 1024
}

# Service catalog (doc 09 §14.3)
$services = @(
    @{name="agent-gateway";            http=8080; grpc=$null; replicas=3; req_cpu="500m";  lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"; tier="gateway"},
    @{name="agent-session";            http=8082; grpc=$null; replicas=2; req_cpu="500m";  lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"; tier="service"},
    @{name="agent-task-orchestrator";  http=8084; grpc=9090;  replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"; tier="service"},
    @{name="agent-memory";             http=8088; grpc=9088;  replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"; tier="service"},
    @{name="agent-tool-engine";        http=8090; grpc=9090;  replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"; tier="service"},
    @{name="agent-runtime";            http=8092; grpc=9092;  replicas=3; req_cpu="500m";  lim_cpu="2000m"; req_mem="1Gi"; lim_mem="4Gi"; tier="runtime"},
    @{name="agent-model-gateway";      http=8094; grpc=9094;  replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"; tier="service"},
    @{name="agent-repo";               http=8096; grpc=9096;  replicas=2; req_cpu="500m";  lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"; tier="service"},
    @{name="agent-knowledge";          http=8098; grpc=9098;  replicas=2; req_cpu="500m";  lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"; tier="service"},
    @{name="agent-quality";            http=8100; grpc=9100;  replicas=2; req_cpu="500m";  lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"; tier="service"},
    @{name="agent-risk-control";       http=8102; grpc=9102;  replicas=2; req_cpu="500m";  lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"; tier="service"},
    @{name="agent-observability";      http=8104; grpc=$null; replicas=2; req_cpu="500m";  lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"; tier="service"}
)

foreach ($svc in $services) {
    $name = $svc.name
    $http = $svc.http
    $grpc = $svc.grpc
    $xms = Convert-MemToMB $svc.req_mem
    $xmx = Convert-MemToMB $svc.lim_mem

    # Build ports block for Deployment
    $portsBlock = @"
            - name: http
              containerPort: $http
              protocol: TCP
"@
    if ($grpc) {
        $portsBlock += "`n            - name: grpc`n              containerPort: $grpc`n              protocol: TCP"
    }

    # Build ports block for Service
    $svcPorts = @"
        - name: http
          port: $http
          targetPort: $http
          protocol: TCP
"@
    if ($grpc) {
        $svcPorts += "`n        - name: grpc`n          port: $grpc`n          targetPort: $grpc`n          protocol: TCP"
    }

    # Deployment yaml
    $depYaml = @"
# $name Deployment (doc 09 §13.2 + §14.3).
# HPA target if applicable (see infra/k8s/hpa/).
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $name
  namespace: agent-platform-prod
  labels:
    app: $name
    tier: $($svc.tier)
spec:
  replicas: $($svc.replicas)
  selector:
    matchLabels:
      app: $name
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: $name
        tier: $($svc.tier)
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "$http"
        prometheus.io/path: /actuator/prometheus
    spec:
      serviceAccountName: ${name}-sa
      terminationGracePeriodSeconds: 60
      containers:
        - name: $name
          image: agentplatform/${name}:1.0.0
          imagePullPolicy: IfNotPresent
          ports:
$portsBlock
          env:
            - name: SPRING_APPLICATION_NAME
              value: $name
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: HOSTNAME_HASH
              valueFrom:
                fieldRef:
                  fieldPath: metadata.uid
            - name: SW_AGENT_NAME
              value: agent-platform-$name
            - name: SW_COLLECTOR_BACKEND_SERVICE
              value: skywalking-oap.agent-platform-infra:11800
            - name: JAVA_OPTS
              value: "-Xms${xms}m -Xmx${xmx}m"
          envFrom:
            - configMapRef:
                name: agent-platform-bootstrap
          resources:
            requests:
              cpu: "$($svc.req_cpu)"
              memory: "$($svc.req_mem)"
            limits:
              cpu: "$($svc.lim_cpu)"
              memory: "$($svc.lim_mem)"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: $http
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: $http
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 5
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: $http
            failureThreshold: 30
            periodSeconds: 10
          lifecycle:
            preStop:
              exec:
                command:
                  - sh
                  - -c
                  - "sleep 10 && curl -X POST http://localhost:${http}/actuator/shutdown || true"
          volumeMounts:
            - name: bootstrap
              mountPath: /app/config
              readOnly: true
            - name: heapdump
              mountPath: /var/log/agent-platform
      volumes:
        - name: bootstrap
          configMap:
            name: agent-platform-bootstrap
        - name: heapdump
          emptyDir: {}
"@
    $depPath = Join-Path $depDir "${name}.yaml"
    Set-Content -Path $depPath -Value $depYaml -Encoding UTF8 -NoNewline
    Write-Host "Generated Deployment: $depPath"

    # Service yaml
    $svcYaml = @"
# $name Service (ClusterIP).
---
apiVersion: v1
kind: Service
metadata:
  name: $name
  namespace: agent-platform-prod
spec:
  type: ClusterIP
  selector:
    app: $name
  ports:
$svcPorts
"@
    $svcPath = Join-Path $svcDir "${name}-svc.yaml"
    Set-Content -Path $svcPath -Value $svcYaml -Encoding UTF8 -NoNewline
    Write-Host "Generated Service: $svcPath"
}

# Ingress for agent-gateway (only externally-exposed service)
$ingressYaml = @"
# Ingress for agent-gateway (only externally-exposed service).
# Apply: kubectl apply -f ingress-gateway.yaml
# Requires nginx ingress controller + TLS secret agent-platform-tls.
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: agent-gateway-ingress
  namespace: agent-platform-prod
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
    nginx.ingress.kubernetes.io/websocket-services: "agent-gateway"
spec:
  ingressClassName: nginx
  tls:
    - hosts: [api.agent-platform.example.com]
      secretName: agent-platform-tls
  rules:
    - host: api.agent-platform.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: agent-gateway
                port:
                  number: 8080
"@
$ingressPath = Join-Path $svcDir "ingress-gateway.yaml"
Set-Content -Path $ingressPath -Value $ingressYaml -Encoding UTF8 -NoNewline
Write-Host "Generated Ingress: $ingressPath"

Write-Host ""
Write-Host "Total: $($services.Count * 2 + 1) files generated."
