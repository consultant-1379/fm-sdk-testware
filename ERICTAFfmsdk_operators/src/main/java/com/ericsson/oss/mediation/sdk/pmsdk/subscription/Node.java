package com.ericsson.oss.mediation.sdk.pmsdk.subscription;

import java.util.ArrayList;

public class Node
{
    private String fdn;
    private String id;
    private String pmFunction;
    private ArrayList<String> technologyDomain;
    private String ossPrefix;
    private String neType;

    public String getFdn()
    {
        return fdn;
    }

    public void setFdn(String fdn)
    {
        this.fdn = fdn;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getPmFunction()
    {
        return pmFunction;
    }

    public void setPmFunction(String pmFunction)
    {
        this.pmFunction = pmFunction;
    }

    public ArrayList<String> getTechnologyDomain()
    {
        return technologyDomain;
    }

    public void setTechnologyDomain(ArrayList<String> technologyDomain)
    {
        this.technologyDomain = technologyDomain;
    }

    public String getOssPrefix()
    {
        return ossPrefix;
    }

    public void setOssPrefix(String ossPrefix)
    {
        this.ossPrefix = ossPrefix;
    }

    public String getNeType()
    {
        return neType;
    }

    public void setNeType(String neType)
    {
        this.neType = neType;
    }

    public String getOssModelIdentity()
    {
        return ossModelIdentity;
    }

    public void setOssModelIdentity(String ossModelIdentity)
    {
        this.ossModelIdentity = ossModelIdentity;
    }

    private String ossModelIdentity;
}
