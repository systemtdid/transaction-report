package com.tdid.txreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.tdid.txreport.config.FontProperties;
import com.tdid.txreport.config.OrgRegistryProperties;

@SpringBootApplication
@EnableConfigurationProperties({OrgRegistryProperties.class, FontProperties.class})
public class TransactionReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionReportApplication.class, args);
    }
}
