// =====================================================================
// File: 01-init-constraints.cypher
// Purpose: Neo4j constraints and indexes for 7 node types
// Source: docs/01-database/database-schema-design.md §11.3
// Node types (per DBA spec): Repository / File / Function / Class / Method / Variable / Import
// Note: doc §11.1 original names: Project/Module/File/Class/Function/Interface/Dependency
//       Here we follow DBA-specified node labels for code knowledge graph.
// =====================================================================

// ----------------------------------------------------------------------
// 1. Uniqueness constraints (7 node types)
// ----------------------------------------------------------------------

// Repository node: code repository root
CREATE CONSTRAINT repository_id_unique IF NOT EXISTS
FOR (n:Repository) REQUIRE n.id IS UNIQUE;

// File node: source file
CREATE CONSTRAINT file_id_unique IF NOT EXISTS
FOR (n:File) REQUIRE n.id IS UNIQUE;

// Function node: function definition
CREATE CONSTRAINT function_id_unique IF NOT EXISTS
FOR (n:Function) REQUIRE n.id IS UNIQUE;

// Class node: class definition
CREATE CONSTRAINT class_id_unique IF NOT EXISTS
FOR (n:Class) REQUIRE n.id IS UNIQUE;

// Method node: method definition (class member function)
CREATE CONSTRAINT method_id_unique IF NOT EXISTS
FOR (n:Method) REQUIRE n.id IS UNIQUE;

// Variable node: variable / field definition
CREATE CONSTRAINT variable_id_unique IF NOT EXISTS
FOR (n:Variable) REQUIRE n.id IS UNIQUE;

// Import node: import / dependency declaration
CREATE CONSTRAINT import_id_unique IF NOT EXISTS
FOR (n:Import) REQUIRE n.id IS UNIQUE;

// ----------------------------------------------------------------------
// 2. Lookup indexes (query performance)
// ----------------------------------------------------------------------

// Function: lookup by name + repo_id (high-frequency call graph query)
CREATE INDEX function_name_repo IF NOT EXISTS
FOR (n:Function) ON (n.name, n.repo_id);

// Repository: lookup by name
CREATE INDEX repository_name IF NOT EXISTS
FOR (n:Repository) ON (n.name);

// File: lookup by path
CREATE INDEX file_path IF NOT EXISTS
FOR (n:File) ON (n.path);

// Class: lookup by name
CREATE INDEX class_name IF NOT EXISTS
FOR (n:Class) ON (n.name);

// Method: lookup by name
CREATE INDEX method_name IF NOT EXISTS
FOR (n:Method) ON (n.name);

// ----------------------------------------------------------------------
// 3. Composite indexes for call-chain traversal
// ----------------------------------------------------------------------

// Function: filter by repo_id + signature (call resolution)
CREATE INDEX function_repo_sig IF NOT EXISTS
FOR (n:Function) ON (n.repo_id, n.signature);

// File: filter by repo_id + language
CREATE INDEX file_repo_lang IF NOT EXISTS
FOR (n:File) ON (n.repo_id, n.language);

// ----------------------------------------------------------------------
// 4. Fulltext index for symbol fuzzy search
// (doc §9.2 governance-and-middleware.md)
// ----------------------------------------------------------------------
CREATE FULLTEXT INDEX symbol_fulltext IF NOT EXISTS
FOR (n:Class|Function|Method|Interface) ON EACH [n.name, n.signature];
