package com.tdid.txreport.exception;

public class UnknownOrgException extends RuntimeException {
    public UnknownOrgException(String orgId) {
        super("Unknown organization ID: " + orgId);
    }
}
