package io.jenkins.plugins;

/**
 * Created by lishaowei on 16/10/8.
 */
public interface DingdingService {


    void start();

    void success();

    void failed();
    
    void abort();
}
