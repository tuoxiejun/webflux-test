package com.example.gateway.config;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "downstream")
public class DownstreamProperties {

    /**
     * Base url for the downstream service.
     */
    private String baseUrl = "http://localhost:8081";

    /**
     * Relative path used when forwarding requests.
     */
    private String path = "/echo";

    /**
     * Header name used to mark the origin of the request.
     */
    private String sourceHeader = "from";

    /**
     * Default value for the {@link #sourceHeader} when the incoming request does not provide one.
     */
    private String defaultSource = "gateway";

    /**
     * Whitelist of keys that will be forwarded from the original request body.
     * When empty, the entire body will be forwarded.
     */
    private Set<String> forwardedKeys = Collections.emptySet();

    /**
     * Additional static fields appended to every forwarded request.
     */
    private Map<String, Object> additionalFields = Collections.emptyMap();

    /**
     * Timeout applied when establishing the downstream connection.
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Timeout applied when waiting for the downstream to respond after the request is sent.
     */
    private Duration responseTimeout = Duration.ofSeconds(5);

    /**
     * Maximum number of connections maintained in the downstream connection pool.
     */
    private int maxConnections = 50;

    /**
     * Maximum number of requests queued while waiting for a connection from the pool.
     */
    private int pendingAcquireMaxCount = 200;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSourceHeader() {
        return sourceHeader;
    }

    public void setSourceHeader(String sourceHeader) {
        this.sourceHeader = sourceHeader;
    }

    public String getDefaultSource() {
        return defaultSource;
    }

    public void setDefaultSource(String defaultSource) {
        this.defaultSource = defaultSource;
    }

    public Set<String> getForwardedKeys() {
        return forwardedKeys == null ? Collections.emptySet() : forwardedKeys;
    }

    public void setForwardedKeys(Set<String> forwardedKeys) {
        this.forwardedKeys = forwardedKeys;
    }

    public Map<String, Object> getAdditionalFields() {
        return additionalFields == null ? Collections.emptyMap() : additionalFields;
    }

    public void setAdditionalFields(Map<String, Object> additionalFields) {
        this.additionalFields = additionalFields;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getPendingAcquireMaxCount() {
        return pendingAcquireMaxCount;
    }

    public void setPendingAcquireMaxCount(int pendingAcquireMaxCount) {
        this.pendingAcquireMaxCount = pendingAcquireMaxCount;
    }
}
