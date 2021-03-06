package org.voltdb.sysprocs;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.voltdb.BackendTarget;
import org.voltdb.DependencySet;
import org.voltdb.HsqlBackend;
import org.voltdb.ParameterSet;
import org.voltdb.ProcInfo;
import org.voltdb.VoltSystemProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltTable.ColumnInfo;
import org.voltdb.VoltType;
import org.voltdb.catalog.Procedure;
import org.voltdb.exceptions.ServerFaultException;
import org.voltdb.types.TimestampType;
import org.voltdb.utils.VoltTableUtil;

import edu.brown.hstore.PartitionExecutor;
import edu.brown.utils.PartitionEstimator;
import edu.brown.utils.ProfileMeasurement;

/** 
 * Force the garbage collector run at each HStoreSite
 */
@ProcInfo(singlePartition = false)
public class GarbageCollection extends VoltSystemProcedure {
    private static final Logger LOG = Logger.getLogger(GarbageCollection.class);

    public static final ColumnInfo nodeResultsColumns[] = {
        new ColumnInfo("SITE", VoltType.STRING),
        new ColumnInfo("STATUS", VoltType.STRING),
        new ColumnInfo("CREATED", VoltType.TIMESTAMP),
    };
    
    private final ProfileMeasurement gcTime = new ProfileMeasurement(this.getClass().getSimpleName());

    @Override
    public void globalInit(PartitionExecutor site, Procedure catalog_proc,
            BackendTarget eeType, HsqlBackend hsql, PartitionEstimator p_estimator) {
        super.globalInit(site, catalog_proc, eeType, hsql, p_estimator);
        site.registerPlanFragment(SysProcFragmentId.PF_gcAggregate, this);
        site.registerPlanFragment(SysProcFragmentId.PF_gcDistribute, this);
    }

    @Override
    public DependencySet executePlanFragment(long txn_id,
                                             Map<Integer, List<VoltTable>> dependencies,
                                             int fragmentId,
                                             ParameterSet params,
                                             PartitionExecutor.SystemProcedureExecutionContext context) {
        DependencySet result = null;
        switch (fragmentId) {
            // Perform Garbage Collection
            case SysProcFragmentId.PF_gcDistribute: {
                LOG.info("Invoking garbage collector");
                this.gcTime.clear();
                this.gcTime.start();
                System.gc();
                this.gcTime.stop();
                
                if (LOG.isDebugEnabled())
                    LOG.debug(String.format("Performed Garbage Collection at %s: %s",
                              this.executor.getHStoreSite().getSiteName(),
                              this.gcTime.debug(true)));
                VoltTable vt = new VoltTable(nodeResultsColumns);
                vt.addRow(this.executor.getHStoreSite().getSiteName(),
                          this.gcTime.getTotalThinkTimeMS() + " ms",
                          new TimestampType());
                result = new DependencySet(SysProcFragmentId.PF_gcDistribute, vt);
                break;
            }
            // Aggregate Results
            case SysProcFragmentId.PF_gcAggregate:
                List<VoltTable> siteResults = dependencies.get(SysProcFragmentId.PF_gcDistribute);
                if (siteResults == null || siteResults.isEmpty()) {
                    String msg = "Missing site results";
                    throw new ServerFaultException(msg, txn_id);
                }
                
                VoltTable vt = VoltTableUtil.combine(siteResults);
                result = new DependencySet(SysProcFragmentId.PF_gcAggregate, vt);
                break;
            default:
                String msg = "Unexpected sysproc fragmentId '" + fragmentId + "'";
                throw new ServerFaultException(msg, txn_id);
        } // SWITCH
        // Invalid!
        return (result);
    }
    
    public VoltTable[] run() {
        return this.autoDistribute(SysProcFragmentId.PF_gcDistribute,
                                   SysProcFragmentId.PF_gcAggregate);
    }
}
