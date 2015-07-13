package com.testfairy.uploader;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;

public final class HttpClientFactory {
	private HttpClientFactory() {}

	public static DefaultHttpClient buildHttpClient() {
		DefaultHttpClient httpClient = new DefaultHttpClient();

		// configure proxy (patched by timothy-volvo, https://github.com/timothy-volvo/testfairy-gradle-plugin)
		String proxyHost = System.getProperty("http.proxyHost");
		if (proxyHost != null) {
			int proxyPort = Integer.parseInt(System.getProperty("http.proxyPort"));
			HttpHost proxy = new HttpHost(proxyHost, proxyPort);
			String proxyUser = System.getProperty("http.proxyUser");
			if (proxyUser != null) {
				AuthScope authScope = new AuthScope(proxyUser, proxyPort);
				Credentials credentials = new UsernamePasswordCredentials(proxyUser, System.getProperty("http.proxyPassword"));
				httpClient.getCredentialsProvider().setCredentials(authScope, credentials);
			}

			httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
		}

		return httpClient;
	}
}
