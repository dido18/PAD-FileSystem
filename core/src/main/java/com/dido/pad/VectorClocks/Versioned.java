package com.dido.pad.VectorClocks;


import com.dido.pad.StorageData;
import com.google.common.base.Preconditions;

/**
 * A wrapper for an StorageData object that adds a Version.
 */

//extends StorageData<?>
public class Versioned<T extends StorageData> {

    private T data;
    private VectorClock vectorclock;


    public Versioned() {
        //for jackson JSOn parser
    }

    public Versioned(T data, VectorClock version) {
        this.data = data;
        this.vectorclock = version;
    }

    public Versioned(T data) {
        this(data, new VectorClock());
    }

    public void setgetVectorclock(VectorClock version) {
        this.vectorclock = version;
    }

    public Version getVersion() {
        return vectorclock;
    }

    public T getData(){
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

