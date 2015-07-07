package it.acsys.aria2wrapper;

import int_.esa.eo.ngeo.downloadmanager.exception.AuthenticationException;
import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.exception.FileSystemWriteException;
import int_.esa.eo.ngeo.downloadmanager.exception.ProductUnavailableException;
import int_.esa.eo.ngeo.downloadmanager.plugin.EDownloadStatus;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import int_.esa.eo.ngeo.downloadmanager.plugin.IProductDownloadListener;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory;


public class IDownloadProcessImpl implements IDownloadProcess {
	private XmlRpcClient client = null;
	private static String rpcUrl;
	private String gid;
	private IProductDownloadListener downloadListener = null;
	private static Logger log = Logger.getLogger(IDownloadProcessImpl.class);
	private boolean notificationOnComplete;
	
	public IDownloadProcessImpl(String rpcUrl, String gid, IProductDownloadListener downloadListener, boolean notificationOnComplete) {
		log.setLevel(Level.DEBUG);
		this.rpcUrl = rpcUrl;
		this.gid = gid;
		this.notificationOnComplete = notificationOnComplete;
		this.downloadListener = downloadListener;
		if(this.downloadListener != null){
			ListenerThread lisThread = new ListenerThread();
			lisThread.start();
		}
		Thread.setDefaultUncaughtExceptionHandler(
            new Thread.UncaughtExceptionHandler() {
                @Override public void uncaughtException(Thread t, Throwable e) {
                	ListenerThread lisThread = new ListenerThread();
        			lisThread.start();
                }
            });
			
		
	}
	
	private  XmlRpcClient getRPCClient() {
		if(client == null) {
			client = new XmlRpcClient();
//			httpClient = new org.apache.commons.httpclient.HttpClient(new org.apache.commons.httpclient.MultiThreadedHttpConnectionManager());
//			XmlRpcCommonsTransportFactory factory = new XmlRpcCommonsTransportFactory(client);
//			factory.setHttpClient(httpClient);
//			client.setTransportFactory(factory);
			
			XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
			config.setEnabledForExtensions(true);
			try {
				config.setServerURL(new URL(rpcUrl));
			} catch(MalformedURLException ex)  	{
				ex.printStackTrace();
			}
			client.setConfig(config);
		}
		
		return client;
	}
	
	public String getProcessId() {
		return this.gid;
	}
	
	public void setProcessId(String gid) {
		this.gid = gid;
	}
	
	
	/*This method changes the position of the download denoted by gid. 
	 * pos is of type integer. how is of type string. If how is "POS_SET", 
	 * it moves the download to a position relative to the beginning of the queue. 
	 * If how is "POS_CUR", it moves the download to a position relative to the current position. 
	 * If how is "POS_END", it moves the download to a position relative to the end of the queue. 
	 * If the destination position is less than 0 or beyond the end of the queue, 
	 * it moves the download to the beginning or the end of the queue respectively. 
	 * The response is of type integer and it is the destination position.*/
	
	public Integer changePosition(int pos, String how) throws DMPluginException {
		Object[] params = new Object[]{this.gid, new Integer(pos), how};
		Integer currPos = null;
		try {
			currPos = (Integer) getRPCClient().execute("aria2.changePosition",params);
		} catch(XmlRpcException ex) {
			throw new DMPluginException(ex.getMessage());
		}
		return currPos;
	}
	
	public Integer changePosition() throws DMPluginException {
		Object[] params = new Object[]{this.gid,0,"POS_SET"};
		Integer id = null;
		try {
			id = (Integer) getRPCClient().execute("aria2.changePosition",params);
		} catch(XmlRpcException ex) {
			throw new DMPluginException(ex.getMessage());
		}
		
		return id;
	}
	
	public List<String> getFiles(String currGid) throws DMPluginException {
		Object[] params = new Object[]{currGid};
		List<String> files = null;
		try {
			HashMap<Object, Object> status = (HashMap<Object, Object>) getRPCClient().execute("aria2.tellStatus",params);
			files = new ArrayList<String>();
			Object[] maps = (Object[]) status.get("files");
			for(int n=0; n<maps.length; n++) {
				files.add(((HashMap<String,String>) maps[n]).get("path"));
			}
			
			
			Object[] following = (Object[]) status.get("followedBy");
			if(following != null) {
				for(int n=0; n < following.length; n++) {
					if(getFiles((String) following[n]) != null)
						files.addAll(getFiles((String) following[n]));
				}
			}
		} catch(XmlRpcException ex)  	{
			throw new DMPluginException(ex.getMessage());
		}
		
		return files;
	}
	
	@Override
	public EDownloadStatus startDownload() throws DMPluginException {
		// TODO Auto-generated method stub
		return null;
	}

	private void pauseDownload(String currGid) throws DMPluginException {
		Object[] params = new Object[]{currGid};
		try {
			HashMap<Object, Object> status = (HashMap<Object, Object>) getRPCClient().execute("aria2.tellStatus",params);
			if(((String) status.get("status")).equals("active")) {
				getRPCClient().execute("aria2.pause",params);
			}
			Object[] following = (Object[]) status.get("followedBy");
			
			if(following != null) {
				for(int n=0; n < following.length; n++) {
					pauseDownload((String) following[n]);
				}
			}		
		} catch(XmlRpcException ex) {
			throw new DMPluginException(ex.getMessage());
		}
	}
	
	@Override
	public EDownloadStatus pauseDownload() throws DMPluginException {
		pauseDownload(this.gid);
		return EDownloadStatus.PAUSED;
	}

	private void resumeDownload(String currGid) throws DMPluginException {
		Object[] params = new Object[]{currGid};
		
		try {
			HashMap<Object, Object> status = (HashMap<Object, Object>) getRPCClient().execute("aria2.tellStatus",params);
			if(((String) status.get("status")).equals("paused")) {
				getRPCClient().execute("aria2.unpause",params);
			}
			Object[] following = (Object[]) status.get("followedBy");
			
			if(following != null) {
				for(int n=0; n < following.length; n++) {
					resumeDownload((String) following[n]);
				}
			}		
		} catch(XmlRpcException ex) {
			throw new DMPluginException(ex.getMessage());
		}
	}
	
	@Override
	public EDownloadStatus resumeDownload() throws DMPluginException {
		resumeDownload(this.gid);
		
		return EDownloadStatus.RUNNING;
	}
	
	
	private void cancelDownload(String currGid) throws DMPluginException {
		Object[] params = new Object[]{currGid};
		try {
			HashMap<Object, Object> status = (HashMap<Object, Object>) getRPCClient().execute("aria2.tellStatus",params);
			if(((String) status.get("status")).equals("active")) {
				getRPCClient().execute("aria2.remove",params);
			}
			Object[] following = (Object[]) status.get("followedBy");
			
			if(following != null) {
				for(int n=0; n < following.length; n++) {
					cancelDownload((String) following[n]);
				}
			}		
		} catch(XmlRpcException ex) {
			throw new DMPluginException(ex.getMessage());
		}
	}

	@Override
	public EDownloadStatus cancelDownload() throws DMPluginException {
		cancelDownload(this.gid);
		return EDownloadStatus.CANCELLED;
	}

	
	public HashMap getStatusMap() throws DMPluginException {
		String[] params = new String[]{gid};
		HashMap status = null;
		try {
			status = (HashMap) getRPCClient().execute("aria2.tellStatus",params);
		} catch(XmlRpcException ex) {
			throw new DMPluginException(ex.getMessage());
		}
		return status;
	}
	
	@Override
	public EDownloadStatus getStatus() {
		String[] params = new String[]{gid};
		HashMap status = null;
		try {
			status = (HashMap) getRPCClient().execute("aria2.tellStatus",params);
		} catch(XmlRpcException ex) {
			ex.printStackTrace();
		}
		if(status != null) {
			if(((String) status.get("status")).equals("waiting")) 
				return EDownloadStatus.NOT_STARTED;
			else if(((String) status.get("status")).equals("active"))
				return EDownloadStatus.RUNNING;
			else if(((String) status.get("status")).equals("paused"))
				return EDownloadStatus.PAUSED;
			else if(((String) status.get("status")).equals("removed"))
				return EDownloadStatus.CANCELLED;
			else if(((String) status.get("status")).equals("error"))
				return EDownloadStatus.IN_ERROR;
			else if(((String) status.get("status")).equals("complete"))
				return EDownloadStatus.COMPLETED;
		}
		return null;
	}


	@Override
	public void disconnect() throws DMPluginException {
		
	}
	
	private EDownloadStatus getStatus(String status ) {
		if(status.equals("active"))
			return EDownloadStatus.RUNNING;
		else if(status.equals("removed"))
			return EDownloadStatus.CANCELLED;
		else if(status.equals("complete"))
			return EDownloadStatus.COMPLETED;
		else if(status.equals("error"))
			return EDownloadStatus.IN_ERROR;
		else if(status.equals("paused"))
			return EDownloadStatus.PAUSED;
		else if(status.equals("waiting")) {
			return EDownloadStatus.RUNNING;
		}
		else return null;
		
	}
	
	public HashMap getGlobalStat() throws XmlRpcException{
		Object[] params = new Object[]{};
		HashMap<String,String> globalStat  =  (HashMap<String,String> ) getRPCClient().execute("aria2.getGlobalStat",params);
		
		return globalStat;
	}
	
	private float getFileSize(String identifier, float totalSize) {
		String[] params = new String[]{identifier};
		HashMap status = null;
		try {
			status = (HashMap) getRPCClient().execute("aria2.tellStatus",params);
		} catch(XmlRpcException ex) {
			log.error("Can not get filesize for " + identifier);
		}
		
		totalSize +=  Float.valueOf((String) status.get("totalLength"));
		Object[] following = (Object[]) status.get("followedBy");
		if(following != null) {
			for(int n=0; n < following.length; n++) {
				totalSize += getFileSize((String) following[n], totalSize);
			}
		}		
		
		return totalSize;
	}
	
	private float getCompletedLength(String identifier,float completedLength) {
		String[] params = new String[]{identifier};
		HashMap status = null;
		try {
			status = (HashMap) getRPCClient().execute("aria2.tellStatus",params);
		} catch(XmlRpcException ex) {
			log.error("Can not get filesize for " + identifier);
		}
		completedLength += Float.valueOf((String) status.get("completedLength"));
		Object[] following = (Object[]) status.get("followedBy");
		if(following != null) {
			for(int n=0; n < following.length; n++) {
				completedLength += getCompletedLength((String)  following[n], completedLength);
			}
		}		
		
		return completedLength;
	}
	
	
	private EDownloadStatus getEDownloadStatus(String identifier,ArrayList<EDownloadStatus> edStatus) {	
		String[] params = new String[]{identifier};
		HashMap status = null;
		try {
			status = (HashMap) getRPCClient().execute("aria2.tellStatus",params);
			
		} catch(XmlRpcException ex) {
			log.error("Can not get filesize for " + identifier);
		}
		Object[] following = (Object[]) status.get("followedBy");
		edStatus.add(getStatus((String) status.get("status")));
		if(following != null) {
			for(int n=0; n < following.length; n++) {
				edStatus.add(getEDownloadStatus((String) following[n], edStatus));
			}
		}		
		
		return extractStatus(edStatus);
	}
	
	private EDownloadStatus extractStatus(ArrayList<EDownloadStatus> edStatus) {
		if(edStatus.contains(EDownloadStatus.IN_ERROR)) {
			return EDownloadStatus.IN_ERROR;
		}
		if(edStatus.contains(EDownloadStatus.CANCELLED)) {
			return EDownloadStatus.CANCELLED;
		}
		if(edStatus.contains(EDownloadStatus.PAUSED)) {
			return EDownloadStatus.PAUSED;
		}
		if(edStatus.contains(EDownloadStatus.RUNNING)) {
			return EDownloadStatus.RUNNING;
		}
		if(edStatus.contains(EDownloadStatus.NOT_STARTED)) {
			return EDownloadStatus.NOT_STARTED;
		}
	
		return EDownloadStatus.COMPLETED;
	}
	
	class ListenerThread extends Thread {
		public void run() {
			boolean toUpdate = true;
			while(toUpdate) {
				int n = 0;
				try {
					HashMap statusMap = getStatusMap();
					int percentage = 0;
//			        float filesize = Float.valueOf((String) statusMap.get("totalLength"));
//			        float completedLength = Float.valueOf((String) statusMap.get("completedLength"));
			        float fileSize = 0f;
			        float completedLength = 0;;
			        fileSize = getFileSize(gid, fileSize);
			        completedLength = getCompletedLength(gid, completedLength);
					
					if(fileSize != 0) {
			        		percentage = (int)  (100.0f * (completedLength/fileSize));
			        		
					}
			        
			        //EDownloadStatus status = getStatus((String) statusMap.get("status"));
			        EDownloadStatus status = getEDownloadStatus(gid, new ArrayList<EDownloadStatus>());

			        DMPluginException ex = null;
			        String message = "";
			        if(status.equals(EDownloadStatus.IN_ERROR)) {
			        	log.debug("ERROR CODE " + (String) statusMap.get("errorCode"));
			        	String errorCode = (String) statusMap.get("errorCode");
			        	
			      //	NullPointer got when purged DB
						if(errorCode!= null && errorCode.equalsIgnoreCase("9")) {
							message = "ERROR: there was not enough disk space available.";
							ex = new FileSystemWriteException(message);
						} else if(errorCode!= null && errorCode.equalsIgnoreCase("16")) {
							message = "ERROR: could not create new file. Permission denied.";
							ex = new FileSystemWriteException(message);
						} else if(errorCode!= null && errorCode.equalsIgnoreCase("18")) {
							message = "ERROR: could not create directory. Permission denied.";
							ex = new FileSystemWriteException(message);
						} else if(errorCode!= null &&  errorCode.equalsIgnoreCase("3")) {
							message = "ERROR: could not find the resource.";
							ex = new ProductUnavailableException(message);
						} else if(errorCode!= null &&  errorCode.equalsIgnoreCase("24")) {
							message = "ERROR: user not authorized.";
							ex = new AuthenticationException(message);
						} else {
							ex = new DMPluginException("DM plugin error code " + (String) statusMap.get("errorCode"));
						}
					}
			        
					if(status.equals(EDownloadStatus.COMPLETED) ||
							status.equals(EDownloadStatus.CANCELLED) ||
							status.equals(EDownloadStatus.IN_ERROR)){
						downloadListener.progress(percentage,(long) fileSize, status, message, ex);						
						toUpdate = false;
						//log.debug("POST PROCESSING FILE WITH GID " + gid + " with postprocessing class " + getClass().getName());
					} else if(!notificationOnComplete) {
						downloadListener.progress(percentage,(long) fileSize, status, message, ex);
			        }
				} catch(DMPluginException e) {
					n++;
					e.printStackTrace();
					log.debug("DMPluginException " + e.getMessage() + " for gid " + gid);
					if(n >= 2) {
						toUpdate = false;
						downloadListener.progress(0, 0L, EDownloadStatus.IN_ERROR, e.getMessage(), e);
//						httpClient.getHttpConnectionManager().closeIdleConnections(0);
					}
				} 
				catch(Exception ex) {
					ex.printStackTrace();
				}
				
				try {
					if(!this.isInterrupted()) {
						Thread.sleep(2000);
					}
				} catch(InterruptedException ex){
					ex.printStackTrace();
				}
			}
			
			this.interrupt();
		}
	}


	@Override
	public File[] getDownloadedFiles() {
		if(this.getStatus().equals(EDownloadStatus.COMPLETED)) {
			File[] files = null;
			try {
				List<String> obj = getFiles(this.gid);
				files = new File[obj.size()];
				int n=0;
				for(String file: obj) {
					files[n] = new File(file);
					n++;
				}
			} catch (DMPluginException e) {
				e.printStackTrace();
			}
			return files;
		} else {
			return null;
		}
	}
	
	

}
