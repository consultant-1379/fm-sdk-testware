package com.ericsson.oss.mediation.sdk.pmsdk.subscription;

import java.util.ArrayList;

import org.codehaus.jackson.annotate.JsonProperty;

public class PmsdkStatisticalSubscription
{

    @JsonProperty ("@class")
    private String classs;

    private String type;
    private Object id;
    private String name;
    private String description;
    private String administrationState;
    private String operationalState;
    private Object userActivationDateTime;
    private Object userDeActivationDateTime;
    private String owner;
    private ScheduleInfo scheduleInfo;
    private String nodeFilter;
    private ArrayList<String> selectedNeTypes;
    private ArrayList<Node> nodes;
    private int numberOfNodes;
    private String userType;
    private ArrayList<Object> criteriaSpecification;
    private String taskStatus;
    private String rop;
    private boolean pnpEnabled;
    private boolean filterOnManagedFunction;
    private boolean filterOnManagedElement;
    private boolean cbs;
    private ArrayList<Counter> counters;

    private String persistenceTime;

    public String getPersistenceTime()
    {
        return persistenceTime;
    }

    public void setPersistenceTime(String persistenceTime)
    {
        this.persistenceTime = persistenceTime;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Object getId()
    {
        return id;
    }

    public void setId(Object id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getAdministrationState()
    {
        return administrationState;
    }

    public void setAdministrationState(String administrationState)
    {
        this.administrationState = administrationState;
    }

    public String getOperationalState()
    {
        return operationalState;
    }

    public void setOperationalState(String operationalState)
    {
        this.operationalState = operationalState;
    }

    public Object getUserActivationDateTime()
    {
        return userActivationDateTime;
    }

    public void setUserActivationDateTime(Object userActivationDateTime)
    {
        this.userActivationDateTime = userActivationDateTime;
    }

    public Object getUserDeActivationDateTime()
    {
        return userDeActivationDateTime;
    }

    public void setUserDeActivationDateTime(Object userDeActivationDateTime)
    {
        this.userDeActivationDateTime = userDeActivationDateTime;
    }

    public String getOwner()
    {
        return owner;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public ScheduleInfo getScheduleInfo()
    {
        return scheduleInfo;
    }

    public void setScheduleInfo(ScheduleInfo scheduleInfo)
    {
        this.scheduleInfo = scheduleInfo;
    }

    public String getNodeFilter()
    {
        return nodeFilter;
    }

    public void setNodeFilter(String nodeFilter)
    {
        this.nodeFilter = nodeFilter;
    }

    public ArrayList<String> getSelectedNeTypes()
    {
        return selectedNeTypes;
    }

    public void setSelectedNeTypes(ArrayList<String> selectedNeTypes)
    {
        this.selectedNeTypes = selectedNeTypes;
    }

    public ArrayList<Node> getNodes()
    {
        return nodes;
    }

    public void setNodes(ArrayList<Node> nodes)
    {
        this.nodes = nodes;
    }

    public int getNumberOfNodes()
    {
        return numberOfNodes;
    }

    public void setNumberOfNodes(int numberOfNodes)
    {
        this.numberOfNodes = numberOfNodes;
    }

    public String getUserType()
    {
        return userType;
    }

    public void setUserType(String userType)
    {
        this.userType = userType;
    }

    public ArrayList<Object> getCriteriaSpecification()
    {
        return criteriaSpecification;
    }

    public void setCriteriaSpecification(ArrayList<Object> criteriaSpecification)
    {
        this.criteriaSpecification = criteriaSpecification;
    }

    public String getTaskStatus()
    {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus)
    {
        this.taskStatus = taskStatus;
    }

    public String getRop()
    {
        return rop;
    }

    public void setRop(String rop)
    {
        this.rop = rop;
    }

    public boolean isPnpEnabled()
    {
        return pnpEnabled;
    }

    public void setPnpEnabled(boolean pnpEnabled)
    {
        this.pnpEnabled = pnpEnabled;
    }

    public boolean isFilterOnManagedFunction()
    {
        return filterOnManagedFunction;
    }

    public void setFilterOnManagedFunction(boolean filterOnManagedFunction)
    {
        this.filterOnManagedFunction = filterOnManagedFunction;
    }

    public boolean isFilterOnManagedElement()
    {
        return filterOnManagedElement;
    }

    public void setFilterOnManagedElement(boolean filterOnManagedElement)
    {
        this.filterOnManagedElement = filterOnManagedElement;
    }

    public boolean isCbs()
    {
        return cbs;
    }

    public void setCbs(boolean cbs)
    {
        this.cbs = cbs;
    }

    public ArrayList<Counter> getCounters()
    {
        return counters;
    }

    public void setCounters(ArrayList<Counter> counters)
    {
        this.counters = counters;
    }

    public String getClasss()
    {
        return classs;
    }

    public void setClasss(String classs)
    {
        this.classs = classs;
    }

}
