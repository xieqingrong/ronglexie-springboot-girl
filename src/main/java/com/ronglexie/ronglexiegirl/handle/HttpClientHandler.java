package com.ronglexie.ronglexiegirl.handle;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * HttpClient 操作具类
 * 默认请求方式: GET
 * @author ronglexie
 * @version 2018-2-7
 */
public class HttpClientHandler {

    /*
    * Available content Types
    * */
    public static final List<ContentType> CONTENT_TYPES = Arrays.asList(
            ContentType.TEXT_PLAIN, ContentType.TEXT_HTML,
            ContentType.TEXT_XML, ContentType.APPLICATION_XML,
            ContentType.APPLICATION_SVG_XML, ContentType.APPLICATION_XHTML_XML,
            ContentType.APPLICATION_ATOM_XML,
            ContentType.APPLICATION_JSON);


    protected static final Logger logger = LoggerFactory.getLogger(HttpClientHandler.class);
    //Convert mill seconds to second unit
    protected static final int MS_TO_S_UNIT = 1000;
    //Normal http response code
    protected static final int NORMAL_RESPONSE_CODE = 200;
    //https prefix
    protected static final String HTTPS = "https";

    protected static HttpsTrustManager httpsTrustManager = new HttpsTrustManager();

    protected String url;

    protected int maxConnectionSeconds = 0;

    protected String contentType;

    protected HttpEntity httpEntity;

    protected Map<String, String> requestParams = new HashMap<>();
    protected Map<String, String> headers = new HashMap<>();


    public HttpClientHandler(String url) {
        this.url = url;
    }

    public HttpClientHandler maxConnectionSeconds(int maxConnectionSeconds) {
        this.maxConnectionSeconds = maxConnectionSeconds;
        return this;
    }

    public HttpClientHandler addRequestParam(String key, String value) {
        this.requestParams.put(key, value);
        return this;
    }


    @SuppressWarnings("unchecked")
    public <T extends HttpClientHandler> T addHeader(String key, String value) {
        this.headers.put(key, value);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends HttpClientHandler> T httpEntity(HttpEntity httpEntity) {
        this.httpEntity = httpEntity;
        return (T) this;
    }


    public HttpClientHandler contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    /*public MonitorLog handleAndGenerateMonitorLog() {
        MonitorLog monitorLog = new MonitorLog();

        final long start = System.currentTimeMillis();
        try {
            final CloseableHttpResponse response = sendRequest();

            monitorLog.setNormal(isNormal(response));
            monitorLog.setResponseSize(responseSize(response));
            monitorLog.setCostTime(costTime(start));

            response.close();
        } catch (Exception e) {
            monitorLog.setCostTime(costTime(start));
            monitorLog.setNormal(false);
            monitorLog.setRemark(e.getClass().getSimpleName() + ": " + e.getMessage());
            logger.debug("Send request to url[" + url + "] failed", e);
        }

        return monitorLog;
    }*/

    public String handleAsString() {
        try {
            final CloseableHttpResponse response = sendRequest();

            if (isNormal(response)) {
                return responseAsString(response);
            } else {
                logger.warn("Failed: Send request to [{}] response: {}", url, response.getStatusLine());
            }

        } catch (Exception e) {
            logger.debug("Send request to url[" + url + "] failed", e);
        }
        return null;
    }


    /*
     * Convert response as string
     */
    protected String responseAsString(CloseableHttpResponse response) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            response.getEntity().writeTo(baos);
            return new String(baos.toByteArray(), "UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    protected long responseSize(CloseableHttpResponse response) {
        return response.getEntity().getContentLength();
    }

    protected long costTime(long start) {
        return System.currentTimeMillis() - start;
    }

    protected boolean isNormal(CloseableHttpResponse response) {
        return response.getStatusLine().getStatusCode() == NORMAL_RESPONSE_CODE;
    }

    public CloseableHttpResponse sendRequest() throws Exception {
        HttpUriRequest request = retrieveHttpRequest();
        setContentType(request);
        setHeaders(request);

        CloseableHttpClient client = retrieveHttpClient();
        return client.execute(request);
    }

    protected void setHttpEntity(RequestBuilder builder) {
        if (this.httpEntity != null) {
            builder.setEntity(this.httpEntity);
        }
    }


    protected void setHeaders(HttpUriRequest request) {
        for (String key : headers.keySet()) {
            request.addHeader(key, headers.get(key));
        }
    }

    private void setContentType(HttpUriRequest request) {
        if (StringUtils.isNotEmpty(this.contentType)) {
            request.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
            logger.debug("Set HttpUriRequest[{}] contentType: {}", request, contentType);
        }
    }

    protected CloseableHttpClient retrieveHttpClient() {
        final RequestConfig requestConfig = requestConfig();
        if (isHttps()) {
            //Support SSL
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(createSSLContext());
            return HttpClients.custom().setDefaultRequestConfig(requestConfig).setSSLSocketFactory(sslConnectionSocketFactory).build();
        } else {
            return HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
        }
    }

    private RequestConfig requestConfig() {
        final int maxConnMillSeconds = this.maxConnectionSeconds * MS_TO_S_UNIT;
        return RequestConfig.custom()
                .setSocketTimeout(maxConnMillSeconds)
                .setConnectTimeout(maxConnMillSeconds).build();
    }


    private SSLContext createSSLContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new HttpsTrustManager[]{httpsTrustManager}, null);
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Create SSLContext error", e);
        }
    }

    protected boolean isHttps() {
        return url.toLowerCase().startsWith(HTTPS);
    }

    private HttpUriRequest retrieveHttpRequest() {
        final RequestBuilder builder = createRequestBuilder();
        addRequestParams(builder);
        setHttpEntity(builder);
        return builder.setUri(url).build();
    }

    protected void addRequestParams(RequestBuilder builder) {
        final Set<String> keySet = requestParams.keySet();
        for (String key : keySet) {
            final String value = requestParams.get(key);
            if (StringUtils.isNotEmpty(key)) {
                builder.addParameter(key, value);
            } else {
                logger.debug("Ignore add request param[{}={}], because key is empty or null", key, value);
            }
        }
    }

    protected RequestBuilder createRequestBuilder() {
        return RequestBuilder.get();
    }


    /**
     * Default X509TrustManager implement
     */
    private static class HttpsTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            //ignore
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            //ignore
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}