package com.securecomcode.voice.monitor;

import com.securecomcode.voice.util.LinuxUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Monitors system level metrics
 */
public class SystemMetrics implements SampledMetrics {
  private String lastStat;
  private Map<String, Object> result = new HashMap<String, Object>();


  @Override
  public Map<String, Object> sample() {
    String stat = LinuxUtils.readSystemStat();
    if(lastStat != null) {
      result.put( "cpu-load", LinuxUtils.getSystemCpuUsage(lastStat, stat));
    }
    lastStat = stat;
    return result;
  }
}
