package com.agent.memory.vectorstore;

import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.AddFieldReq;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Milvus collection schema for agent_memory_vector (Plan 03 T6).
 *
 * <p>Schema aligned with doc 04-memory §7.3:</p>
 * <ul>
 *   <li>id (VARCHAR, PK) — memory business ID</li>
 *   <li>tenant_id (VARCHAR) — tenant isolation</li>
 *   <li>vector (FLOAT_VECTOR, dim=1024) — embedding vector</li>
 *   <li>importance (FLOAT) — importance score [0,1]</li>
 *   <li>status (VARCHAR) — MemoryStatus enum name</li>
 *   <li>created_at (INT64) — epoch millis</li>
 * </ul>
 *
 * <p>Index: IVF_FLAT, nlist=1024, MetricType=COSINE (doc 04 §7.4)</p>
 */
@Slf4j
public class MilvusSchemaBuilder {

    public static final String FIELD_ID = "id";
    public static final String FIELD_TENANT_ID = "tenant_id";
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_IMPORTANCE = "importance";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_CREATED_AT = "created_at";

    public static final int VECTOR_DIM = 1024;
    public static final int IVF_NLIST = 1024;

    private MilvusSchemaBuilder() {
        // utility class
    }

    /**
     * Build CreateCollectionReq with schema + index params.
     *
     * @param collectionName collection name (typically "agent_memory_vector")
     * @return create collection request
     */
    public static CreateCollectionReq buildSchema(String collectionName) {
        // Define fields
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .build();

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_ID)
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .autoID(false)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_TENANT_ID)
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(VECTOR_DIM)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_IMPORTANCE)
                .dataType(DataType.Float)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_STATUS)
                .dataType(DataType.VarChar)
                .maxLength(32)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CREATED_AT)
                .dataType(DataType.Int64)
                .build());

        // Define index on vector field
        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(IndexParam.builder()
                .fieldName(FIELD_VECTOR)
                .indexType(IndexParam.IndexType.IVF_FLAT)
                .metricType(IndexParam.MetricType.COSINE)
                .build());

        // Index on tenant_id for filtering
        indexParams.add(IndexParam.builder()
                .fieldName(FIELD_TENANT_ID)
                .indexType(IndexParam.IndexType.STL_SORT)
                .build());

        return CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();
    }
}
