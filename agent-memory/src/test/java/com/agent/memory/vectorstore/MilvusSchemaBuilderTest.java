package com.agent.memory.vectorstore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link MilvusSchemaBuilder} (Plan 03 T6).
 *
 * <p>Verifies schema field constants and buildSchema output structure.</p>
 */
@DisplayName("MilvusSchemaBuilder - Schema Constants")
class MilvusSchemaBuilderTest {

    @Test
    @DisplayName("should_HaveCorrectFieldNames")
    void should_HaveCorrectFieldNames() {
        assertThat(MilvusSchemaBuilder.FIELD_ID).isEqualTo("id");
        assertThat(MilvusSchemaBuilder.FIELD_TENANT_ID).isEqualTo("tenant_id");
        assertThat(MilvusSchemaBuilder.FIELD_VECTOR).isEqualTo("vector");
        assertThat(MilvusSchemaBuilder.FIELD_IMPORTANCE).isEqualTo("importance");
        assertThat(MilvusSchemaBuilder.FIELD_STATUS).isEqualTo("status");
        assertThat(MilvusSchemaBuilder.FIELD_CREATED_AT).isEqualTo("created_at");
    }

    @Test
    @DisplayName("should_HaveCorrectVectorDimension")
    void should_HaveCorrectVectorDimension() {
        assertThat(MilvusSchemaBuilder.VECTOR_DIM).isEqualTo(1024);
    }

    @Test
    @DisplayName("should_HaveCorrectIvfNlist")
    void should_HaveCorrectIvfNlist() {
        assertThat(MilvusSchemaBuilder.IVF_NLIST).isEqualTo(1024);
    }

    @Test
    @DisplayName("should_BuildSchemaSuccessfully")
    void should_BuildSchemaSuccessfully() {
        var req = MilvusSchemaBuilder.buildSchema("test_collection");
        assertThat(req).isNotNull();
        assertThat(req.getCollectionName()).isEqualTo("test_collection");
    }
}
