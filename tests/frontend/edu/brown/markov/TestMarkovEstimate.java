package edu.brown.markov;

import org.voltdb.VoltProcedure;
import org.voltdb.benchmark.tpcc.procedures.slev;
import org.voltdb.catalog.Procedure;

import edu.brown.BaseTestCase;
import edu.brown.catalog.CatalogUtil;
import edu.brown.utils.ProjectType;

public class TestMarkovEstimate extends BaseTestCase {

    private static final Class<? extends VoltProcedure> TARGET_PROCEDURE = slev.class;
    private static final int NUM_PARTITIONS = 16;
    private static final int BASE_PARTITION = 2;
    private static final float THRESHOLD_LEVEL = 0.8f;
    
    private MarkovGraph markov;
    private MarkovEstimate est;
    private Procedure catalog_proc;
    private EstimationThresholds thresholds = new EstimationThresholds(THRESHOLD_LEVEL);
    
    @Override
    protected void setUp() throws Exception {
        super.setUp(ProjectType.TPCC);
        this.addPartitions(NUM_PARTITIONS);
        
        this.catalog_proc = this.getProcedure(TARGET_PROCEDURE);
        this.markov = new MarkovGraph(this.catalog_proc).initialize();
        
        this.est = new MarkovEstimate(CatalogUtil.getNumberOfPartitions(catalog_db));
        assertFalse(this.est.isValid());
    }
    
    /**
     * testProbabilities
     */
    public void testProbabilities() throws Exception {
        est.init(markov.getStartVertex(), MarkovEstimate.INITIAL_ESTIMATE_BATCH);
        
        // Initialize
        // This is based on an actual estimate generated from a benchmark run
        for (int p = 0, cnt = est.getNumPartitions(); p < cnt; p++) {
            est.setReadOnlyProbability(p, 1.0f);
            if (p == BASE_PARTITION) {
                est.setWriteProbability(p, 0.08f);
                est.setDoneProbability(p, 0.0f);
                est.incrementTouchedCounter(p);
            } else {
                est.setWriteProbability(p, 0.0f);
                est.setDoneProbability(p, 1.0f);
            }
        } // FOR
        est.setConfidenceProbability(0.92f);
        est.setSingleSitedProbability(1.0f);
        est.setAbortProbability(0.0f);
        assert(this.est.isValid());
//        System.err.println(est);
        
        assertEquals(true, est.isSinglePartition(thresholds));
        assertEquals(true, est.isReadOnlyAllPartitions(thresholds));
        assertEquals(false, est.isAbortable(thresholds));
    }
    
}
