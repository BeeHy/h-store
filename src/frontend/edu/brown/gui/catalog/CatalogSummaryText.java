package edu.brown.gui.catalog;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.voltdb.catalog.Catalog;
import org.voltdb.catalog.Cluster;
import org.voltdb.catalog.Column;
import org.voltdb.catalog.Database;
import org.voltdb.catalog.MaterializedViewInfo;
import org.voltdb.catalog.Procedure;
import org.voltdb.catalog.Table;

import edu.brown.catalog.CatalogUtil;
import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;
import edu.brown.utils.StringUtil;

public class CatalogSummaryText {
    private static final Logger LOG = Logger.getLogger(CatalogSummaryText.class);
    private static final LoggerBoolean debug = new LoggerBoolean(LOG.isDebugEnabled());
    private static final LoggerBoolean trace = new LoggerBoolean(LOG.isTraceEnabled());
    static {
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    
    final Catalog catalog;
    
    public CatalogSummaryText(Catalog catalog) {
        this.catalog = catalog;
    }
    
    /**
     * Return summary text about the catalog
     */
    @SuppressWarnings("unchecked")
    public String getSummaryText() {
        Map<String, Integer> m[] = (Map<String, Integer>[])new Map<?, ?>[3];
        int idx = -1;
        
        // ----------------------
        // TABLE INFO
        // ----------------------
        m[++idx] = new LinkedHashMap<String, Integer>();
        int cols = 0;
        int fkeys = 0;
        int tables = 0;
        int systables = 0;
        int views = 0;
        Map<Table, MaterializedViewInfo> catalog_views = CatalogUtil.getVerticallyPartitionedTables(catalog);
        Cluster catalog_clus = CatalogUtil.getCluster(catalog);
        Database catalog_db = CatalogUtil.getDatabase(catalog);
        for (Table t : catalog_db.getTables()) {
            if (catalog_views.values().contains(t)) {
                views++;
            }
            else if (t.getSystable()) {
                systables++;
            } else {
                tables++;
                cols += t.getColumns().size();
                for (Column c : t.getColumns()) {
                    Column fkey = CatalogUtil.getForeignKeyParent(c);
                    if (fkey != null) fkeys++;
                }
            }
        } // FOR
        m[idx].put("Tables", tables);
        m[idx].put("Columns", cols);
        m[idx].put("Foreign Keys", fkeys);
        m[idx].put("Views", views);
        m[idx].put("Vertical Replicas", systables);
        m[idx].put("System Tables", systables);
        
        // ----------------------
        // PROCEDURES INFO
        // ----------------------
        m[++idx] = new LinkedHashMap<String, Integer>();
        int procs = 0;
        int sysprocs = 0;
        int params = 0;
        int stmts = 0;
        for (Procedure p : catalog_db.getProcedures()) {
            if (p.getSystemproc()) {
                sysprocs++;
            } else {
                procs++;
                params += p.getParameters().size();
                stmts += p.getStatements().size();
            }
        }
        m[idx].put("Procedures", procs);
        m[idx].put("Procedure Parameters", params);
        m[idx].put("Statements", stmts);
        m[idx].put("System Procedures", sysprocs);
        
        // ----------------------
        // HOST INFO
        // ----------------------
        m[++idx] = new LinkedHashMap<String, Integer>();
        m[idx].put("Hosts", catalog_clus.getHosts().size());
        m[idx].put("Sites", catalog_clus.getSites().size());
        m[idx].put("Partitions", CatalogUtil.getNumberOfPartitions(catalog_db));
        
        StringBuilder buffer = new StringBuilder();
        buffer.append(StringUtil.header("Catalog Summary", "-", 50) + "\n\n")
              .append(StringUtil.formatMaps(m));
        return (buffer.toString());
    }
}
