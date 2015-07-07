package it.acsys.aria2wrapper;


import int_.esa.eo.ngeo.downloadmanager.exception.AuthenticationException;
import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.EDownloadStatus;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPlugin;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPluginInfo;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import int_.esa.eo.ngeo.downloadmanager.plugin.IProductDownloadListener;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import com.siemens.pse.umsso.client.UmssoCLCore;
import com.siemens.pse.umsso.client.UmssoCLCoreImpl;
import com.siemens.pse.umsso.client.UmssoCLInput;
import com.siemens.pse.umsso.client.UmssoCLOutput;
import com.siemens.pse.umsso.client.UmssoHttpGet;
import com.sun.org.apache.xml.internal.security.utils.Base64;

public class URIDownloader implements IDownloadPlugin {
//	private static XmlRpcClient client = null;
	private static String rpcURL;
	private static Logger log = Logger.getLogger(URIDownloader.class);
	private boolean isUMSSOAuth = false;
	private boolean isBasicAuth = false;
	private boolean notificationOnComplete = false;
	
	public URIDownloader() {
	}
	
	public void setRpcUrl(String rpcURL) {
		this.rpcURL = rpcURL;
	}
	
	public IDownloadProcess createDownloadProcess(URI productURI, File repositoryDir, String user, String password, IProductDownloadListener downloadListener, String proxyLocation, int proxyPort, String proxyUser, String proxyPassword ) 
				throws DMPluginException {
		log.setLevel(Level.DEBUG);
		Map<String, String> map = new HashMap<String, String>();
		if(!repositoryDir.exists()) {
			repositoryDir.mkdir();
		}
		map.put("dir", repositoryDir.getAbsolutePath());
		if(isUMSSOAuth) {
//		RETRIEVE HEADER TO PUT INTO ARIA REQUEST
			UmssoCLCore clCore = UmssoCLCoreImpl.getInstance();
			java.util.List<Cookie> cookies = clCore.getUmssoHttpClient().getCookieStore().getCookies();
			String cookie_saml = "_saml_idp=";
	  	  	String cookie_shib = "_shibsession_=";
			for(Cookie cookie : cookies) {
				if(cookie.getName().equals("_saml_idp")) {
					cookie_saml = cookie.getName() + "=" + cookie.getValue();
				}
				if(cookie.getName().contains("_shibsession_")) {
					cookie_shib = cookie.getName() + "="+ cookie.getValue();
				}
			}
			
			if(cookie_saml.equalsIgnoreCase("_saml_idp=") || cookie_shib.equals("_shibsession_=")) {
				UmssoCLInput input = new UmssoCLInput();
				input.setVisualizerCallback(new CommandLineCallback(user, password.toCharArray()));
				UmssoHttpGet reqMethod = new UmssoHttpGet(productURI.toString());
				input.setAppHttpMethod(reqMethod);
				
				try {
			  	  	KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			        trustStore.load(null, null);
		
			        SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
			        sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		
			        clCore.getUmssoHttpClient().getConnectionManager().getSchemeRegistry().register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			    	clCore.getUmssoHttpClient().getConnectionManager().getSchemeRegistry().register(new Scheme("https", sf, 443));
					//long initialTime = System.currentTimeMillis();
			    	clCore.processHttpRequest(input);
			    	//long finalTime = System.currentTimeMillis();
					//System.out.println("STATUS FOR " + productURI.toString() + " " + out.getStatus() + " in " + (finalTime-initialTime) + " msec.");
					
					cookies = clCore.getUmssoHttpClient().getCookieStore().getCookies();
					for(Cookie cookie : cookies) {
						if(cookie.getName().equals("_saml_idp")) {
							cookie_saml = cookie.getName() + "=" + cookie.getValue();
						}
						if(cookie.getName().contains("_shibsession_")) {
							cookie_shib = cookie.getName() + "="+ cookie.getValue();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					reqMethod.releaseConnection();
				}
			}
	
	        if(cookie_saml.equalsIgnoreCase("_saml_idp=") || cookie_shib.equals("_shibsession_=")) {
	        	log.debug("SSO ERROR FOR "  + productURI.toString());
				downloadListener.progress(0,0l, EDownloadStatus.IN_ERROR, "GID null", new AuthenticationException("User not valid"));
				throw new AuthenticationException("User not valid");
	        }
			String cookie = cookie_saml + "; " + cookie_shib;
//			log.debug("cookie " + cookie);
			//reqMethod.releaseConnection();
			String header = "Cookie: " + cookie;
			map.put("header", header);
			log.debug("Sending request to aria with header " + header);
		} else if(isBasicAuth) {
			String header = "Authorization: Basic " + Base64.encode((user + ":" + password).getBytes());
			map.put("header", header);
			log.debug("Sending request to aria with header " + header);
		}
		
		Object[] params = null;
		
//		FOR S2 NO decode URL for expiration key problems!!!!!
//		try {
//			String realURL = URLDecoder.decode(productURI.toString(), "UTF-8");
//			log.debug("URI DOWNLOADER PLUGIN REAL URL " + realURL);
//			params = new Object[]{new String[]{realURL}, map};
//		} catch(UnsupportedEncodingException ex)  	{
//			ex.printStackTrace();
//		}
		
		params = new Object[]{new String[]{productURI.toString()}, map};
		String gid = null;
		int n= 0;
		while (gid == null  && n < 3) {
//			if(n > 0) {
//				System.out.println("RETRYING TO ADD URI "  + productURI.toString());
//			}
			n++;
			try {			
				gid = (String) getRPCClient().execute("aria2.addUri", params);					
			} catch(XmlRpcException ex) {
				ex.printStackTrace();
			}
			
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if(gid == null) {
			log.debug("After 3 retries GID NULL FOR "  + productURI.toString());
			downloadListener.progress(0,0l, EDownloadStatus.IN_ERROR, "GID null", null);
			return null;
		}
		
		IDownloadProcessImpl process =  new IDownloadProcessImpl(rpcURL,gid, downloadListener,notificationOnComplete);
//		HashMap<String,String> status = null;
//		try {
//			status = process.getStatusMap();
//		} catch(DMPluginException ex) {
//			ex.printStackTrace();
//		}
//		long fileSize = -1;
//		int numPieces = -1;
//		if(status != null) {
//			try {
//				
//					fileSize = Long.valueOf((String) status.get("totalLength"));
//				
//			} catch (NumberFormatException e) {
//				log.error("filesize unknown for product " + gid);
//			}
//			try {
//				
//				numPieces = Integer.valueOf((String) status.get("numPieces"));
//				 
//			} catch (NumberFormatException e) {
//				log.error("Number of pieces unknown for product " + gid);
//			}
//		}
//		downloadListener.productDetails(gid, numPieces, fileSize);
		return process;
	}
	
	private  XmlRpcClient getRPCClient() {
		//if(client == null) {
			XmlRpcClient client = new XmlRpcClient();
			XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
			config.setEnabledForExtensions(true);
			try {
				config.setServerURL(new URL(rpcURL));
			} catch(MalformedURLException ex)  	{
				ex.printStackTrace();
			}
			client.setConfig(config);
		//}
		
		return client;
	}
	
	public void terminate() throws DMPluginException {
//		Object[] params = new Object[]{};
//		try {
//			getRPCClient().execute("aria2.shutdown",params);
//		} catch(XmlRpcException ex)  	{
//			throw new DMPluginException(ex.getMessage());
//		}
		
	}
	
	public IDownloadPluginInfo initialize(File tmpRootDir, File pluginCfgRootDir) throws DMPluginException {
		try {
			java.io.InputStream stream  = new java.io.FileInputStream(pluginCfgRootDir);
			Properties properties = new Properties();
	    	properties.load(stream);
	    	stream.close();
			this.setRpcUrl("http://localhost:" + properties.getProperty("ariaPort") +"/rpc");
			this.setUMSSOAuth(Boolean.valueOf(properties.getProperty("isUMSSOAuth")));
			this.setBasicAuth(Boolean.valueOf(properties.getProperty("isBasicAuth")));
			this.setNotificationOnComplete(Boolean.valueOf(properties.getProperty("notificationOnComplete")));
		} catch (IOException e) {
            e.printStackTrace();
        }
		
		
		return new IDownloadPluginInfoImpl();
		
	}
	
	private void setNotificationOnComplete(Boolean value) {
		if (value == null || !value) {
			notificationOnComplete = false;
		} else {
			notificationOnComplete = true;
		}
	}
	
	private void setUMSSOAuth(Boolean value) {
		if (value == null || !value) {
			isUMSSOAuth = false;
		} else {
			isUMSSOAuth = true;
		}
	}
	
	private void setBasicAuth(Boolean value) {
		if (value == null || !value) {
			isBasicAuth = false;
		} else {
			isBasicAuth = true;
		}
	}
	
}
