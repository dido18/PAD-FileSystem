package com.dido.pad;


import com.dido.pad.consistenthashing.HashableData;
import com.dido.pad.consistenthashing.Hasher;
import com.google.code.gossip.*;
import com.google.code.gossip.event.GossipListener;
import com.google.code.gossip.event.GossipState;
import org.apache.log4j.Logger;

import java.net.UnknownHostException;
import java.util.List;

/**
 * Created by dido-ubuntu on 05/03/16.
 */
public class Node  {

    public static final Logger LOGGER = Logger.getLogger(Node.class);

    private GossipService _gossipService;
    private StorageService _dataService;

    private String ipAddress;
    private String id;


    public Node(String ipAdresss, String id){
        this.ipAddress = ipAdresss;
        this.id = id;
        this._dataService = new StorageService(this);
    }

    public StorageService get_dataService() {
        return _dataService;
    }

    public Node(GossipMember member){
        this(member.getAddress(), member.getId());
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public void addGossipService(int port, int logLevel, List<GossipMember> gossipMembers, GossipSettings settings, GossipListener listener)
            throws UnknownHostException, InterruptedException {
        _gossipService = new GossipService(this.ipAddress,port,this.id,LogLevel.DEBUG,gossipMembers,settings,listener);
    }

    public void startGossipService(){
        _gossipService.start();
    }

    public void shutdown(){
        _gossipService.shutdown();
    }



    /* callback of gossiping procedure if a node goes UP or DOWN  */
    public void  gossipEvent(GossipMember member, GossipState state) {
        switch (state) {
            case UP:
                getHasher().addServer(new Node(member));
                Node.LOGGER.info("Node "+this.toString()+" ADDS  "+member.getAddress());
                break;
            case DOWN:
                getHasher().removeServer(new Node(member));
                Node.LOGGER.info("Node "+this.toString()+"  REMOVES "+member.getAddress());
                break;

        };
    }

    public Hasher<Node,HashableData> getHasher(){
        return _dataService.getcHasher();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        if (!ipAddress.equals(node.ipAddress)) return false;
        return id.equals(node.id);

    }

    @Override
    public int hashCode() {
        int result = ipAddress.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Node{" +
                "id='" + id + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                '}';
    }


}
