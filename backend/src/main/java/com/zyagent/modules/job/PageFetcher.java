package com.zyagent.modules.job;

import java.io.IOException;

@FunctionalInterface
public interface PageFetcher {
    String fetch(String url) throws IOException;
}
