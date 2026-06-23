package com.tdid.txreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "txreport.fonts")
public class FontProperties {

    private String noto;
    private String tahoma;
    private String sarabun;

    public String getNoto() { return noto; }
    public void setNoto(String noto) { this.noto = noto; }

    public String getTahoma() { return tahoma; }
    public void setTahoma(String tahoma) { this.tahoma = tahoma; }

    public String getSarabun() { return sarabun; }
    public void setSarabun(String sarabun) { this.sarabun = sarabun; }
}
