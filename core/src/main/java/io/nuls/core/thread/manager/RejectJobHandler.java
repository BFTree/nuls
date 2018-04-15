package io.nuls.core.thread.manager;

import io.nuls.core.utils.log.Log;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Desription:
 * @Author: PierreLuo
 * @Date:
 */
public class RejectJobHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        Log.error("RejectedExecution info: " + r.toString());
        throw new RejectedExecutionException("Task " + r.toString() +
                " rejected from " +
                e.toString());
    }

}
