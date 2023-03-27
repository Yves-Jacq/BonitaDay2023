package org.bonitasoft.reporting.services;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

class ReportingExecutor {
    private static ThreadPoolExecutor executor = null;

    static ThreadPoolExecutor getInstance(){
        if(executor ==null){
            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
            Executors.newSingleThreadExecutor()
        }
       return executor;
    }
}
