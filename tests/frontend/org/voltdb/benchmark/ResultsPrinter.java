/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.voltdb.benchmark;

import org.voltdb.benchmark.BenchmarkResults.Result;

public class ResultsPrinter implements BenchmarkController.BenchmarkInterest {

    @Override
    public void benchmarkHasUpdated(BenchmarkResults results) {

        long totalTxnCount = 0;
        for (String client : results.getClientNames()) {
            for (String txn : results.getTransactionNames()) {
                Result[] rs = results.getResultsForClientAndTransaction(client, txn);
                for (Result r : rs)
                    totalTxnCount += r.transactionCount;
            }
        }

        long txnDelta = 0;
        for (String client : results.getClientNames()) {
            for (String txn : results.getTransactionNames()) {
                Result[] rs = results.getResultsForClientAndTransaction(client, txn);
                Result r = rs[rs.length - 1];
                txnDelta += r.transactionCount;
            }
        }

        int pollIndex = results.getCompletedIntervalCount();
        long duration = results.getTotalDuration();
        long pollCount = duration / results.getIntervalDuration();
        long currentTime = pollIndex * results.getIntervalDuration();

        System.out.printf("\nAt time %d out of %d (%d%%):\n", currentTime, duration, currentTime * 100 / duration);
        System.out.printf("  In the past %d ms:\n", duration / pollCount);
        System.out.printf("    Completed %d txns at a rate of %.2f txns/s\n",
                txnDelta,
                txnDelta / (double)(results.getIntervalDuration()) * 1000.0);
        System.out.printf("  Since the benchmark began:\n");
        System.out.printf("    Completed %d txns at a rate of %.2f txns/s\n",
                totalTxnCount,
                totalTxnCount / (double)(pollIndex * results.getIntervalDuration()) * 1000.0);


        if ((pollIndex * results.getIntervalDuration()) >= duration) {
            // print the final results
            System.out.println("\n================================ BENCHMARK RESULTS ================================");
            System.out.printf("Time: %d ms\n", duration);
            System.out.printf("Total transactions: %d\n", totalTxnCount);
            System.out.printf("Transactions per second: %.2f\n", totalTxnCount / (double)duration * 1000.0);
            for (String transactionName : results.getTransactionNames()) {
                final long txnCount = getTotalCountForTransaction(transactionName, results);
                System.out.printf("%23s: %10d total %-6s %8.2f txn/s %10.2f txn/m\n",
                        transactionName,
                        txnCount,
                        String.format("(%5.1f%%)", (txnCount / (double)totalTxnCount) * 100), 
                        txnCount / (double)duration * 1000.0,
                        txnCount / (double)duration * 1000.0 * 60.0);
            }
            System.out.println("Breakdown by client:");
            for (String clientName : results.getClientNames()) {
                final long txnCount = getTotalCountForClient(clientName, results);
                clientName = clientName.replace("client-", "");
                System.out.printf("%23s: %10d total %-8s %8.2f txn/s %10.2f txn/m\n",
                        clientName,
                        txnCount,
                        "",
                        txnCount / (double)duration * 1000.0,
                        txnCount / (double)duration * 1000.0 * 60.0);
            }
            System.out.println("===================================================================================\n");
        }

        System.out.flush();
    }

    private long getTotalCountForClient(String clientName, BenchmarkResults results) {
        long txnCount = 0;
        for (String txnName : results.getTransactionNames()) {
            Result[] rs = results.getResultsForClientAndTransaction(clientName, txnName);
            for (Result r : rs)
                txnCount += r.transactionCount;
        }
        return txnCount;
    }

    private long getTotalCountForTransaction(String txnName, BenchmarkResults results) {
        long txnCount = 0;
        for (String clientName : results.getClientNames()) {
            Result[] rs = results.getResultsForClientAndTransaction(clientName, txnName);
            for (Result r : rs)
                txnCount += r.transactionCount;
        }
        return txnCount;
    }

}
