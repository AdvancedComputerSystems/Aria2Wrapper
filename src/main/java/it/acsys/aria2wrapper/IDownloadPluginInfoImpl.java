package it.acsys.aria2wrapper;

import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPluginInfo;

public class IDownloadPluginInfoImpl implements IDownloadPluginInfo {

	@Override
	public String getName() {
		return "Aria2 wrapper plugin";
	}

	@Override
	public int[] getPluginVersion() {
		return new int[]{1};
	}

	@Override
	public String[] getMatchingPatterns() {
		return null;
	}

	@Override
	public int[] getDMMinVersion() {
		return new int[]{1};
	}

	@Override
	public boolean handlePause() {
		return true;
	}

}
