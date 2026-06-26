// =====================================================================
// File: 02-init-relationships.cypher
// Purpose: Neo4j relationship type examples + sample data
// Source: docs/01-database/database-schema-design.md §11.2
// Relationship types (per DBA spec):
//   CONTAINS / CALLS / INHERITS / IMPLEMENTS / IMPORTS / DEFINES
// Note: doc §11.2 original: CONTAINS/CALLS/IMPLEMENTS/EXTENDS/DEPENDS_ON/IMPORTS
//       Here we follow DBA-specified relationship types.
// =====================================================================

// ----------------------------------------------------------------------
// Relationship type documentation:
//
// CONTAINS     : Repository -> File / File -> Function / File -> Class
// CALLS        : Function -> Function / Method -> Method
// INHERITS     : Class -> Class (replaces doc EXTENDS)
// IMPLEMENTS   : Class -> Interface
// IMPORTS      : File -> File / File -> Import
// DEFINES      : File -> Function / File -> Class / File -> Method / File -> Variable
// ----------------------------------------------------------------------

// ----------------------------------------------------------------------
// Sample data: build a small code knowledge graph
// Scenario: a simple repository with 2 files, 3 functions, 1 class, 1 method
// ----------------------------------------------------------------------

// Step 1: Create Repository node
MERGE (repo:Repository {id: 'repo_001', name: 'agent-platform-prototype', language: 'java', version: '1.0.0'});

// Step 2: Create File nodes
MERGE (f1:File {id: 'file_001', path: 'src/main/java/com/agentplatform/task/TaskService.java', language: 'java', repo_id: 'repo_001', sha: 'abc123'});
MERGE (f2:File {id: 'file_002', path: 'src/main/java/com/agentplatform/task/TaskRepository.java', language: 'java', repo_id: 'repo_001', sha: 'def456'});

// Step 3: Repository -[:CONTAINS]-> File
MATCH (repo:Repository {id: 'repo_001'}), (f1:File {id: 'file_001'})
MERGE (repo)-[:CONTAINS]->(f1);
MATCH (repo:Repository {id: 'repo_001'}), (f2:File {id: 'file_002'})
MERGE (repo)-[:CONTAINS]->(f2);

// Step 4: Create Function nodes
MERGE (fn1:Function {id: 'fn_001', name: 'createTask', signature: 'Task createTask(TaskRequest req)', repo_id: 'repo_001', return_type: 'Task'});
MERGE (fn2:Function {id: 'fn_002', name: 'findById', signature: 'Optional<Task> findById(String taskId)', repo_id: 'repo_001', return_type: 'Optional<Task>'});
MERGE (fn3:Function {id: 'fn_003', name: 'updateStatus', signature: 'void updateStatus(String taskId, String status)', repo_id: 'repo_001', return_type: 'void'});

// Step 5: File -[:DEFINES]-> Function
MATCH (f1:File {id: 'file_001'}), (fn1:Function {id: 'fn_001'})
MERGE (f1)-[:DEFINES]->(fn1);
MATCH (f1:File {id: 'file_001'}), (fn3:Function {id: 'fn_003'})
MERGE (f1)-[:DEFINES]->(fn3);
MATCH (f2:File {id: 'file_002'}), (fn2:Function {id: 'fn_002'})
MERGE (f2)-[:DEFINES]->(fn2);

// Step 6: Function -[:CALLS]-> Function (call graph)
// createTask calls findById to check duplicates
MATCH (fn1:Function {id: 'fn_001'}), (fn2:Function {id: 'fn_002'})
MERGE (fn1)-[:CALLS {callSite: 'line 45', line: 45}]->(fn2);
// createTask calls updateStatus after creation
MATCH (fn1:Function {id: 'fn_001'}), (fn3:Function {id: 'fn_003'})
MERGE (fn1)-[:CALLS {callSite: 'line 52', line: 52}]->(fn3);

// Step 7: Create Class and Method nodes
MERGE (cls1:Class {id: 'cls_001', name: 'TaskService', modifiers: 'public', startLine: 20, repo_id: 'repo_001'});
MERGE (m1:Method {id: 'm_001', name: 'validateTask', signature: 'boolean validateTask(Task t)', repo_id: 'repo_001', return_type: 'boolean'});

// Step 8: File -[:CONTAINS]-> Class / Class -[:DEFINES]-> Method
MATCH (f1:File {id: 'file_001'}), (cls1:Class {id: 'cls_001'})
MERGE (f1)-[:CONTAINS]->(cls1);
MATCH (cls1:Class {id: 'cls_001'}), (m1:Method {id: 'm_001'})
MERGE (cls1)-[:DEFINES]->(m1);

// Step 9: Method -[:CALLS]-> Function (cross-type call)
MATCH (m1:Method {id: 'm_001'}), (fn2:Function {id: 'fn_002'})
MERGE (m1)-[:CALLS {callSite: 'line 35', line: 35}]->(fn2);

// Step 10: Create Variable nodes (class fields)
MERGE (v1:Variable {id: 'var_001', name: 'taskRepository', type: 'TaskRepository', repo_id: 'repo_001'});
MERGE (v2:Variable {id: 'var_002', name: 'logger', type: 'Logger', repo_id: 'repo_001'});

// Step 11: Class -[:DEFINES]-> Variable
MATCH (cls1:Class {id: 'cls_001'}), (v1:Variable {id: 'var_001'})
MERGE (cls1)-[:DEFINES]->(v1);
MATCH (cls1:Class {id: 'cls_001'}), (v2:Variable {id: 'var_002'})
MERGE (cls1)-[:DEFINES]->(v2);

// Step 12: Create Import nodes + IMPORTS relationships
MERGE (imp1:Import {id: 'imp_001', name: 'java.util.Optional', module: 'java.util'});
MERGE (imp2:Import {id: 'imp_002', name: 'org.slf4j.Logger', module: 'org.slf4j'});

MATCH (f1:File {id: 'file_001'}), (imp1:Import {id: 'imp_001'})
MERGE (f1)-[:IMPORTS {line: 3}]->(imp1);
MATCH (f1:File {id: 'file_001'}), (imp2:Import {id: 'imp_002'})
MERGE (f1)-[:IMPORTS {line: 4}]->(imp2);

// ----------------------------------------------------------------------
// Verification queries (run manually to verify graph)
// ----------------------------------------------------------------------

// Query 1: List all relationship types and counts
// CALL db.relationshipTypes() YIELD relationshipType
// RETURN relationshipType, count(*) AS cnt;

// Query 2: Find all callers of a function (call graph upward)
// MATCH (caller:Function)-[:CALLS*1..5]->(target:Function {name: 'findById'})
// RETURN caller.name, caller.signature, length(p) AS depth LIMIT 50;

// Query 3: Find all functions defined in a file
// MATCH (f:File {path: 'src/main/java/com/agentplatform/task/TaskService.java'})-[:DEFINES]->(fn:Function)
// RETURN fn.name, fn.signature;
