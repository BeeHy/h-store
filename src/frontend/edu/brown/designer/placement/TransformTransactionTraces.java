package edu.brown.designer.placement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

import org.apache.log4j.Logger;
import org.voltdb.catalog.Database;
import org.voltdb.catalog.Host;
import org.voltdb.catalog.Procedure;
import org.voltdb.catalog.Site;
import org.voltdb.catalog.Table;
import org.voltdb.types.QueryType;

import edu.brown.catalog.CatalogUtil;
import edu.brown.designer.MemoryEstimator;
import edu.brown.hashing.DefaultHasher;
import edu.brown.statistics.Histogram;
import edu.brown.utils.ArgumentsParser;
import edu.brown.utils.PartitionEstimator;
import edu.brown.workload.QueryTrace;
import edu.brown.workload.TransactionTrace;

class TxnPartition {
    int basePartition;
    List<Set<Integer>> partitions = new ArrayList<Set<Integer>>();
    
    public TxnPartition(int basePartition) {
        this.basePartition = basePartition;
    }
    
    public List<Set<Integer>> getPartitions() {
        return partitions;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("base partition: " + String.valueOf(basePartition) + " ");
        for (Set<Integer> batch : partitions) {
            for (Integer partition_id : batch) {
                sb.append(String.valueOf(partition_id) + " ");
            }
        }
        return sb.toString();
    }
}
public class TransformTransactionTraces {

    private static final Logger LOG = Logger.getLogger(TransformTransactionTraces.class);
    
    public static void transform(List<TransactionTrace> txn_traces, PartitionEstimator est, Database catalogDb, long partition_size, ArgumentsParser args) {
    	
        Histogram<Integer> hist = new Histogram<Integer>();
        List<TxnPartition> al_txn_partitions = new ArrayList<TxnPartition>();

        /** keep the count on the number of times that each transaction access a partition per batch. **/
        Map<Integer, Integer> txn_partition_counts = new HashMap<Integer, Integer>();
        TxnPartition txn_partitions;

        int total_num_partitions = CatalogUtil.getNumberOfPartitions(catalogDb);
        int total_num_transactions = txn_traces.size(); // ???
        int total_touched_partitions = 0;
        int total_num_batches = 0;
        int[][] output_matrix = new int[total_num_partitions][total_num_partitions];
        
        for (TransactionTrace trace : txn_traces) {
            int base_partition = 0;
            try {
                base_partition = est.getBasePartition(trace);
                hist.put(base_partition);
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            txn_partitions = new TxnPartition(base_partition);
            int batch = 0;
            for (Integer batch_id : trace.getBatchIds()) {
                // list of query traces
                Set<Integer> total_query_partitions = new HashSet<Integer>();
                for (QueryTrace qt :trace.getBatches().get(batch_id)) {
                    try {
                    	Set<Integer> batch_partitions = est.getAllPartitions(qt, base_partition);
                    	for (int partition : batch_partitions) {
                    		hist.put(partition);
                    		output_matrix[base_partition][partition]++;
                    		total_touched_partitions++;
                    	}
                        total_query_partitions.addAll(batch_partitions);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                txn_partitions.getPartitions().add(total_query_partitions);
                batch++;
            }
            total_num_batches += batch;
            al_txn_partitions.add(txn_partitions);
        }

        System.out.println("Total num batches in workload: " + total_num_batches);
        StringBuilder sb = new StringBuilder();
        sb.append(CatalogUtil.getNumberOfHosts(catalogDb) + " " + CatalogUtil.getNumberOfSites(catalogDb) + " " + CatalogUtil.getNumberOfPartitions(catalogDb) + "\n");
        for (int hist_value : hist.values()) {
        	//LOG.info(hist_value  + ": " + hist.get(hist_value));
    		sb.append(((hist.get(hist_value) * 1.0) / total_num_batches) + " " + partition_size + " " + args.getParam("simulator.host.memory") + "\n");
        }
        //sb.append(hist.toString());
        // write the file
        writeFile(al_txn_partitions, sb, args, output_matrix, total_touched_partitions);
    }
    
    /**
     * File format like the following:
     * # of hosts, # of sites, # of partitions
     * heat (partition 1) total partition size (partition 1) total available memory (partition 1)
     * ...
     * ...
     * heat (partition n) total partition size (partition n) total available memory (partition n)
     * [affinity stuff]
     * partition 0 ..... partition n
     * ........... ..... ...........
     * ........... ..... ...........
     * partition n ..... partition n
     * EOF
     * @param xact_partitions
     */
    public static void writeFile(List<TxnPartition> xact_partitions, StringBuilder sb, ArgumentsParser args, int[][] matrix, int total_touched_partitions) {
        assert (xact_partitions.size() > 0);
//        TxnPartition xact_partition = xact_partitions.get(0);
//        StringBuilder sb = new StringBuilder();
//        sb.append(xact_partition.getPartitions().size() + " " + xact_partitions.size() + "\n");
//        for (TxnPartition txn_partition: xact_partitions) {
//            sb.append(txn_partition.basePartition + "\n");
//            for (Set<Integer> partition_set : txn_partition.getPartitions()) {
//                for (Integer p_id : partition_set) {
//                    sb.append(p_id + " ");
//                }
//                sb.append("\n");
//            }
//        }
        //LOG.info("\n");
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
            	sb.append(matrix[i][j] * 1.0 / total_touched_partitions + " ");
            }
            sb.append("\n");
        }
        Writer writer;
        try {
            writer = new FileWriter(args.getOptParam(0));
            writer.append(sb.toString());
            writer.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    public static void main(String[] vargs) throws Exception {
        ArgumentsParser args = ArgumentsParser.load(vargs);
        args.require(
                ArgumentsParser.PARAM_CATALOG,
                ArgumentsParser.PARAM_WORKLOAD,
                ArgumentsParser.PARAM_STATS,
                ArgumentsParser.PARAM_SIMULATOR_HOST_MEMORY
            );        
        MemoryEstimator estimator = new MemoryEstimator(args.stats, new DefaultHasher(args.catalog_db, CatalogUtil.getNumberOfPartitions(args.catalog_db)));
        long partition_size = (estimator.estimateTotalSize(args.catalog_db) / CatalogUtil.getNumberOfPartitions(args.catalog));
        PartitionEstimator p_estimator = new PartitionEstimator(args.catalog_db);
        transform(args.workload.getTransactions(), p_estimator, args.catalog_db, partition_size, args);
    }

}