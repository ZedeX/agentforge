package k8s.rbac

# R-04: Prevent Group-level RoleBindings (must use per-SA binding)
deny[msg] {
    input.kind == "RoleBinding"
    subject := input.subjects[_]
    subject.kind == "Group"
    msg := sprintf("RoleBinding '%s' must not bind to Group (use per-SA binding)", [input.metadata.name])
}

# R-04: Prevent 'list' verb on secrets (use per-SA 'get' only)
deny[msg] {
    input.kind == "Role"
    rule := input.rules[_]
    rule.resources[_] == "secrets"
    rule.verbs[_] == "list"
    msg := sprintf("Role '%s' must not grant 'list' on secrets (use per-SA 'get' only)", [input.metadata.name])
}

# Prevent 'watch' verb on secrets
deny[msg] {
    input.kind == "Role"
    rule := input.rules[_]
    rule.resources[_] == "secrets"
    rule.verbs[_] == "watch"
    msg := sprintf("Role '%s' must not grant 'watch' on secrets", [input.metadata.name])
}
