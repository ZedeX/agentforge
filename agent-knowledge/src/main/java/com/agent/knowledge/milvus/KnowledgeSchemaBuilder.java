package com.agent.knowledge.milvus;

import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.AddFieldReq;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Milvus collection schema for knowledge chunks (Plan 08 T10).
 *
 * <p>Schema per KB collection (kb_{kbId}):</p>
 * <ul>
 *   <li>chunk_id (VARCHAR, PK) — chunk identifier</li>
 *   <li>doc_id (VARCHAR) — document identifier</li>
 *   <li>vector (FLOAT_VECTOR, dim=1024) — embedding vector</li>
 *   <li>content (VARCHAR) — chunk text content</li>
 * </ul>
 *
 * <p>Index: IVF_FLAT, COSINE (aligned with doc 07-knowledge §6.1).</p>
 */
@Slf4j
public class KnowledgeSchemaBuilder {

    public static final String FIELD_CHUNK_ID = "chunk_id";
    public static final String FIELD_DOC_ID = "doc_id";
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_CONTENT = "content";

    public static final int VECTOR_DIM = 1024;
    public static final int IVF_NLIST = 1024;

    private KnowledgeSchemaBuilder() {
        // utility class
    }

    /**
     * Build CreateCollectionReq with schema + index params.
     *
     * @param collectionName collection name (typically "kb_{kbId}")
     * @return create collection request
     */
    public static CreateCollectionReq buildSchema(String collectionName) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .build();

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_ID)
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .autoID(false)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_DOC_ID)
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(VECTOR_DIM)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CONTENT)
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());

        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(IndexParam.builder()
                .fieldName(FIELD_VECTOR)
                .indexType(IndexParam.IndexType.IVF_FLAT)
                .metricType(IndexParam.MetricType.COSINE)
                .build());

        indexParams.add(IndexParam.builder()
                .fieldName(FIELD_DOC_ID)
                .indexType(IndexParam.IndexType.STL_SORT)
                .build());

        return CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();
    }
}
