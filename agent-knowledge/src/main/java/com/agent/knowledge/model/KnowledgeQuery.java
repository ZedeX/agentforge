package com.agent.knowledge.model;

/**
 * Knowledge retrieval query (doc 07-knowledge §7.1 search request).
 *
 * <p>Carries kbId + query text + topK + MMR flag + lambda for diversity reranking.</p>
 */
public class KnowledgeQuery {

    private final String kbId;
    private final String query;
    private final int topK;
    private final boolean useMmr;
    private final double mmrLambda;

    public KnowledgeQuery(String kbId, String query, int topK, boolean useMmr, double mmrLambda) {
        this.kbId = kbId;
        this.query = query;
        this.topK = topK;
        this.useMmr = useMmr;
        this.mmrLambda = mmrLambda;
    }

    public String getKbId() { return kbId; }

    public String getQuery() { return query; }

    public int getTopK() { return topK; }

    public boolean isUseMmr() { return useMmr; }

    public double getMmrLambda() { return mmrLambda; }
}
