package com.agent.knowledge.milvus;

import io.milvus.v2.service.collection.request.CreateCollectionReq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link KnowledgeSchemaBuilder} (Plan 08 T10).
 *
 * <p>Verifies schema field constants and buildSchema output structure.
 * This test runs without a live Milvus instance — it only validates
 * the schema definition metadata.</p>
 */
@DisplayName("KnowledgeSchemaBuilder - Schema Constants (Plan 08 T10)")
class KnowledgeSchemaBuilderTest {

    @Test
    @DisplayName("should_HaveCorrectFieldNames")
    void should_HaveCorrectFieldNames() {
        assertThat(KnowledgeSchemaBuilder.FIELD_CHUNK_ID).isEqualTo("chunk_id");
        assertThat(KnowledgeSchemaBuilder.FIELD_DOC_ID).isEqualTo("doc_id");
        assertThat(KnowledgeSchemaBuilder.FIELD_VECTOR).isEqualTo("vector");
        assertThat(KnowledgeSchemaBuilder.FIELD_CONTENT).isEqualTo("content");
    }

    @Test
    @DisplayName("should_HaveCorrectVectorDimension")
    void should_HaveCorrectVectorDimension() {
        assertThat(KnowledgeSchemaBuilder.VECTOR_DIM).isEqualTo(1024);
    }

    @Test
    @DisplayName("should_HaveCorrectIvfNlist")
    void should_HaveCorrectIvfNlist() {
        assertThat(KnowledgeSchemaBuilder.IVF_NLIST).isEqualTo(1024);
    }

    @Test
    @DisplayName("should_BuildSchemaSuccessfully_When_ValidCollectionName")
    void should_BuildSchemaSuccessfully_When_ValidCollectionName() {
        CreateCollectionReq req = KnowledgeSchemaBuilder.buildSchema("kb_test_kb_001");
        assertThat(req).isNotNull();
        assertThat(req.getCollectionName()).isEqualTo("kb_test_kb_001");
    }

    @Test
    @DisplayName("should_BuildSchemaSuccessfully_When_KbPrefixedCollectionName")
    void should_BuildSchemaSuccessfully_When_KbPrefixedCollectionName() {
        CreateCollectionReq req = KnowledgeSchemaBuilder.buildSchema("kb_12345");
        assertThat(req).isNotNull();
        assertThat(req.getCollectionName()).isEqualTo("kb_12345");
    }
}
