"""
File: 01-init-collections.py
Purpose: Initialize 6 Milvus Collections with HNSW index
Source: docs/01-database/database-schema-design.md §10
        + task description (embedding dim=768)
Requirements: pymilvus>=2.4.0
Usage:
    python 01-init-collections.py --host <milvus-host> --port 19530 \
        --user <user> --password <password>
"""

import argparse
import logging
import sys
from typing import List

from pymilvus import (
    MilvusClient,
    DataType,
    CollectionSchema,
    FieldSchema,
    Collection,
    utility,
    connections,
)

# ----------------------------------------------------------------------
# Logging configuration
# ----------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler("milvus_init.log", encoding="utf-8"),
    ],
)
log = logging.getLogger("milvus_init")

EMBEDDING_DIM = 768
HNSW_M = 16
HNSW_EF_CONSTRUCTION = 256
METRIC_TYPE = "COSINE"


# ----------------------------------------------------------------------
# Collection definitions
# ----------------------------------------------------------------------
def build_memory_episodic_schema() -> CollectionSchema:
    """memory_episodic: episodic memory vectors (doc §10.1 mem_episodic)."""
    fields = [
        FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="tenant_id", dtype=DataType.INT64),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
        FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=8192),
        FieldSchema(name="source_task_id", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="domain", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="importance_score", dtype=DataType.FLOAT),
        FieldSchema(name="created_at", dtype=DataType.INT64),
        FieldSchema(name="valid", dtype=DataType.BOOL),
    ]
    return CollectionSchema(fields=fields, description="Episodic memory vector collection")


def build_memory_semantic_schema() -> CollectionSchema:
    """memory_semantic: semantic memory vectors (doc §10.1 mem_semantic)."""
    fields = [
        FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="tenant_id", dtype=DataType.INT64),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
        FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=8192),
        FieldSchema(name="domain", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="created_at", dtype=DataType.INT64),
        FieldSchema(name="valid", dtype=DataType.BOOL),
    ]
    return CollectionSchema(fields=fields, description="Semantic memory vector collection")


def build_memory_procedural_schema() -> CollectionSchema:
    """memory_procedural: procedural memory vectors (doc §10.1 mem_procedural)."""
    fields = [
        FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="tenant_id", dtype=DataType.INT64),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
        FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=8192),
        FieldSchema(name="template_type", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="created_at", dtype=DataType.INT64),
        FieldSchema(name="valid", dtype=DataType.BOOL),
    ]
    return CollectionSchema(fields=fields, description="Procedural memory vector collection")


def build_knowledge_chunk_schema() -> CollectionSchema:
    """knowledge_chunk: knowledge base chunk vectors (doc §10.1 kb_chunks)."""
    fields = [
        FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="base_id", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
        FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=8192),
        FieldSchema(name="chunk_type", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="created_at", dtype=DataType.INT64),
    ]
    return CollectionSchema(fields=fields, description="Knowledge chunk vector collection")


def build_code_symbol_schema() -> CollectionSchema:
    """code_symbol: code symbol vectors (doc §10.1 code_snippet)."""
    fields = [
        FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="repo_id", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
        FieldSchema(name="symbol_name", dtype=DataType.VARCHAR, max_length=128),
        FieldSchema(name="symbol_type", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="file_path", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="line_range", dtype=DataType.VARCHAR, max_length=32),
    ]
    return CollectionSchema(fields=fields, description="Code symbol vector collection")


def build_agent_ability_schema() -> CollectionSchema:
    """agent_ability: agent ability vectors (doc §10.1 tool_index extension)."""
    fields = [
        FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="agent_id", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
        FieldSchema(name="ability_tags", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="description", dtype=DataType.VARCHAR, max_length=2048),
    ]
    return CollectionSchema(fields=fields, description="Agent ability vector collection")


COLLECTION_SPECS = [
    {
        "name": "memory_episodic",
        "schema_fn": build_memory_episodic_schema,
        "partition_key": "domain",
        "partitions": ["order", "cs", "code", "general"],
    },
    {
        "name": "memory_semantic",
        "schema_fn": build_memory_semantic_schema,
        "partition_key": "domain",
        "partitions": ["order", "cs", "code", "general"],
    },
    {
        "name": "memory_procedural",
        "schema_fn": build_memory_procedural_schema,
        "partition_key": "template_type",
        "partitions": ["dag", "prompt", "tool"],
    },
    {
        "name": "knowledge_chunk",
        "schema_fn": build_knowledge_chunk_schema,
        "partition_key": "base_id",
        "partitions": ["kb_default", "kb_code"],
    },
    {
        "name": "code_symbol",
        "schema_fn": build_code_symbol_schema,
        "partition_key": "repo_id",
        "partitions": ["repo_default"],
    },
    {
        "name": "agent_ability",
        "schema_fn": build_agent_ability_schema,
        "partition_key": "agent_id",
        "partitions": ["ag_default"],
    },
]


def create_hnsw_index(collection: Collection) -> None:
    """Create HNSW index on embedding field (M=16, efConstruction=256, COSINE)."""
    index_params = {
        "index_type": "HNSW",
        "metric_type": METRIC_TYPE,
        "params": {"M": HNSW_M, "efConstruction": HNSW_EF_CONSTRUCTION},
    }
    collection.create_index(field_name="embedding", index_params=index_params)
    log.info("  HNSW index created (M=%d, efConstruction=%d, metric=%s)",
             HNSW_M, HNSW_EF_CONSTRUCTION, METRIC_TYPE)


def create_partitions(collection: Collection, partition_names: List[str]) -> None:
    """Create partitions for the collection."""
    existing = {p.name for p in collection.partitions}
    for name in partition_names:
        if name not in existing:
            collection.create_partition(name)
            log.info("  Partition created: %s", name)


def init_collection(spec: dict) -> None:
    """Create or recreate a single collection with index and partitions."""
    name = spec["name"]
    if utility.has_collection(name):
        log.warning("Collection '%s' already exists, dropping for re-init", name)
        utility.drop_collection(name)

    schema = spec["schema_fn"]()
    collection = Collection(name=name, schema=schema)
    log.info("Collection created: %s", name)

    create_hnsw_index(collection)
    create_partitions(collection, spec["partitions"])
    collection.load()
    log.info("Collection '%s' loaded into memory", name)


def main() -> int:
    parser = argparse.ArgumentParser(description="Initialize Milvus collections")
    parser.add_argument("--host", default="localhost", help="Milvus host")
    parser.add_argument("--port", type=int, default=19530, help="Milvus port")
    parser.add_argument("--user", default="", help="Milvus user")
    parser.add_argument("--password", default="", help="Milvus password")
    parser.add_argument("--db", default="default", help="Milvus database name")
    args = parser.parse_args()

    log.info("=== Milvus Collections Init Start ===")
    log.info("Host=%s Port=%d DB=%s", args.host, args.port, args.db)

    connections.connect(
        alias="default",
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.db,
    )
    log.info("Connected to Milvus")

    for spec in COLLECTION_SPECS:
        try:
            init_collection(spec)
        except Exception as e:
            log.error("Failed to init collection '%s': %s", spec["name"], e)
            return 1

    log.info("=== Milvus Collections Init Done (6 collections) ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())
