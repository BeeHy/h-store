package edu.brown.designer;

import java.io.File;
import java.util.*;

import org.voltdb.catalog.*;

import edu.brown.catalog.CatalogUtil;
import edu.brown.catalog.DependencyUtil;
import edu.brown.costmodel.AbstractCostModel;
import edu.brown.costmodel.SingleSitedCostModel;
import edu.brown.designer.generators.DependencyGraphGenerator;
import edu.brown.designer.indexselectors.*;
import edu.brown.designer.mappers.*;
import edu.brown.designer.partitioners.*;
import edu.brown.graphs.*;
import edu.brown.hashing.DefaultHasher;
import edu.brown.mappings.ParameterMappingsSet;
import edu.brown.statistics.WorkloadStatistics;
import edu.brown.utils.ArgumentsParser;
import edu.brown.workload.*;

/**
 * Container object for the various information that a designer will need to use
 * to create a new physical database design.
 * @author pavlo
 *
 */
public class DesignerInfo {
    /**
     * Cache of DependencyGraphs for Database catalog objects
     */
    private final static Map<Database, DependencyGraph> DGRAPH_CACHE = new HashMap<Database, DependencyGraph>(); 
    
    /**
     * Keep this around in case we need it later on
     */
    private final ArgumentsParser args;
    
    /**
     * The base database catalog object that the designer is creating a physical plan for 
     */
    public final Database catalog_db;
    
    /**
     * The DependencyGraph of the schema
     */
    public final DependencyGraph dgraph;
    
    /**
     * The sample workload trace used as the basis of the physical design 
     */
    public Workload workload;
    
    /**
     * WorkloadStatistics
     */
    public WorkloadStatistics stats;
    
    /**
     * Dependencies
     */
    public final DependencyUtil dependencies;
    
    /**
     * ProcParameter Correlations
     */
    private ParameterMappingsSet mappings;
    private String correlations_file;
    
    /**
     * Designer Components
     * Defaults defined in ArgumentParser
     */
    public Class<? extends AbstractPartitioner> partitioner_class;
    public Class<? extends AbstractMapper> mapper_class;
    public Class<? extends AbstractIndexSelector> indexer_class;

    /**
     * Cost Model Components
     */
    public Class<? extends AbstractCostModel> costmodel_class = SingleSitedCostModel.class;
    private AbstractCostModel costmodel;
    private MemoryEstimator m_estimator;
    
    /**
     * Number of concurrent threads to use
     */
    private int num_threads;
    
    /**
     * The number of time intervals to use when dividing up the workload information
     */
    private int num_intervals;
    
    /**
     * Number of partitions
     */
    private final int num_partitions;
    
    /**
     * Where the designer can write out checkpoint information
     */
    private File checkpoint;
    
    /**
     * Copy Constructor
     * @param src
     */
    public DesignerInfo(DesignerInfo src) {
        this.args = src.args;
        this.catalog_db = src.catalog_db;
        this.workload = src.workload;
        this.stats = src.stats;
        this.partitioner_class = src.partitioner_class;
        this.mapper_class = src.mapper_class;
        this.indexer_class = src.indexer_class;
        this.num_threads = src.num_threads;
        this.num_intervals = src.num_intervals;
        this.num_partitions = src.num_partitions;
        this.dependencies = src.dependencies;
        this.costmodel_class = src.costmodel_class;
        this.costmodel = src.costmodel;
        this.checkpoint = src.checkpoint;
        this.m_estimator = src.m_estimator;
        this.mappings = src.mappings;
        this.correlations_file = src.correlations_file;
        this.dgraph = src.dgraph;
    }
    
    /**
     * 
     * @param catalog_db
     * @param workload
     * @param stats
     * @param hints
     */
    public DesignerInfo(ArgumentsParser args) {
        this.args = args;
        this.catalog_db = args.catalog_db;
        this.workload = args.workload;
        this.stats = args.stats;
        this.partitioner_class = args.partitioner_class;
        this.mapper_class = args.mapper_class;
        this.indexer_class = args.indexer_class;
        this.num_threads = args.max_concurrent;
        this.num_intervals = args.num_intervals;
        this.num_partitions = CatalogUtil.getNumberOfPartitions(catalog_db);
        this.dependencies = DependencyUtil.singleton(this.catalog_db);
        this.costmodel_class = args.costmodel_class;
        this.costmodel = args.costmodel;
        this.checkpoint = args.designer_checkpoint;
       
        // Memory Estimator
        this.m_estimator = new MemoryEstimator(this.stats, new DefaultHasher(this.catalog_db, this.num_partitions));
        
        // Correlations (smoke 'em if you got 'em)
        if (args.param_mappings != null) {
            this.mappings = args.param_mappings;
            this.correlations_file = args.getParam(ArgumentsParser.PARAM_MAPPINGS);
        }
        
        if (!DesignerInfo.DGRAPH_CACHE.containsKey(this.catalog_db)) {
            this.dgraph = new DependencyGraph(this.catalog_db);
            try {
                new DependencyGraphGenerator(this).generate(this.dgraph);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
            DesignerInfo.DGRAPH_CACHE.put(this.catalog_db, this.dgraph);
        } else {
            this.dgraph = DesignerInfo.DGRAPH_CACHE.get(this.catalog_db);
        }
    }

    /**
     * Constructor
     * @param args
     */
    public DesignerInfo(final Database _catalog_db, final Workload _workload, final WorkloadStatistics _stats) {
        this(new ArgumentsParser() {{
            this.catalog_db = _catalog_db;
            this.workload = _workload;
            this.stats = _stats;
        }});
    }
    
    /**
     * Constructor
     * @param catalog_db
     * @param workload
     */
    public DesignerInfo(Database catalog_db, Workload workload) {
        this(catalog_db, workload, new WorkloadStatistics(catalog_db));
    }
    
    public Workload getWorkload() {
        return this.workload;
    }
    public void setWorkload(Workload workload) {
        this.workload = workload;
    }

    /** Parameter Mappings **/
    public void setMappings(ParameterMappingsSet mappings) {
        this.mappings = mappings;
    }
    public ParameterMappingsSet getMappings() {
        return mappings;
    }
    public void setMappingsFile(String file) {
        correlations_file = file;
    }
    public String getMappingsFile() {
        return correlations_file;
    }
    
    /** Workload Statistics **/
    public void setStats(WorkloadStatistics stats) {
        this.stats = stats;
        this.m_estimator = new MemoryEstimator(this.stats, this.m_estimator.getHasher());
    }
    public WorkloadStatistics getStats() {
        return this.stats;
    }
    
    /** DependencyUtil **/
    public DependencyUtil getDependencies() {
        return this.dependencies;
    }
    
    public AbstractDirectedGraph<DesignerVertex, DesignerEdge> getDependencyGraph() {
        return this.dgraph;
    }
    
    public ArgumentsParser getArgs() {
        return this.args;
    }
    
    public Class<? extends AbstractPartitioner> getPartitionerClass() {
        return partitioner_class;
    }
    public void setPartitionerClass(Class<? extends AbstractPartitioner> partitionerClass) {
        partitioner_class = partitionerClass;
    }

    public Class<? extends AbstractMapper> getMapperClass() {
        return mapper_class;
    }
    public void setMapperClass(Class<? extends AbstractMapper> mapperClass) {
        mapper_class = mapperClass;
    }

    public Class<? extends AbstractIndexSelector> getIndexerClass() {
        return indexer_class;
    }
    public void setIndexerClass(Class<? extends AbstractIndexSelector> indexerClass) {
        indexer_class = indexerClass;
    }

    public Class<? extends AbstractCostModel> getCostModelClass() {
        return costmodel_class;
    }
    public void setCostModelClass(Class<? extends AbstractCostModel> costModelClass) {
        costmodel_class = costModelClass;
    }
    
    public AbstractCostModel getCostModel() {
        return costmodel;
    }
    public void setCostModel(AbstractCostModel costmodel) {
        this.costmodel = costmodel;
    }
    
    public MemoryEstimator getMemoryEstimator() {
        return (this.m_estimator);
    }

    public int getNumThreads() {
        return this.num_threads;
    }
    public void setNumThreads(int num_threads) {
        this.num_threads = num_threads;
    }
    
    public int getNumIntervals() {
        return this.num_intervals;
    }
    public void setNumIntervals(int numIntervals) {
        this.num_intervals = numIntervals;
    }

    public int getNumPartitions() {
        return num_partitions;
    }
    
    
    public void setCheckpointFile(File checkpoint) {
        this.checkpoint = checkpoint;
    }
    public File getCheckpointFile() {
        return this.checkpoint;
    }
    
}
