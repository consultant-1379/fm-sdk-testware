package com.ericsson.oss.mediation.sdk.pmsdk.subscription;

public class Counter
{
    private String name;
    public String getName()
    {
        return name;
    }
    public void setName(String name)
    {
        this.name = name;
    }
    public String getMoClassType()
    {
        return moClassType;
    }
    public void setMoClassType(String moClassType)
    {
        this.moClassType = moClassType;
    }
    private String moClassType;
}
