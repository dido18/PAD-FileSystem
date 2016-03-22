package com.dido.pad;

import com.dido.pad.datamessages.AppMsg;
import com.google.code.gossip.*;
import com.google.code.gossip.event.GossipState;
import com.google.code.gossip.manager.GossipManager;
import org.apache.log4j.Logger;


import java.net.*;
import java.util.List;

/**
 * Created by dido-ubuntu on 05/03/16.
 */

public class Node {

    public static final Logger LOGGER = Logger.getLogger(Node.class);

    private GossipService _gossipService;
    private StorageService _storageService;

    private String ipAddress;
    private String id;

    private int portStorage;
    private int portGossip;
    private int numReplicas;

    // Empty constructor for jackson parser to JSON
    public Node() {
    }

    public int getNumReplicas() {
        return numReplicas;
    }

    public void setNumReplicas(int numReplicas) {
        this.numReplicas = numReplicas;
    }

    public Node(String ipAddress, String id, int portStorage, int portGossip, List<GossipMember> gossipMembers) {
        this(ipAddress, id, portStorage, portGossip, gossipMembers, new GossipSettings());
    }

    public Node(String ipAddress, String id, int portStorage, int portGossip, List<GossipMember> gossipMembers, GossipSettings settings) {
        this.ipAddress = ipAddress;
        this.id = id;
        this.portStorage = portStorage;
        this.portGossip = portGossip;
        try {
            _gossipService = new GossipService(ipAddress, this.portGossip, id, gossipMembers, settings, this::gossipEvent);
            _storageService = new StorageService(this, gossipMembers);
            _storageService.addServer(this);

        } catch (InterruptedException | UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        _gossipService.start();
        _storageService.start();
    }

    // Node from a GossipMember.
    // Used when a GossipMemeber goes UP
    public Node(GossipMember member) {
        this.ipAddress = member.getHost();
        this.id = member.getId();
        this.portStorage = Helper.STORAGE_PORT;
        this.portGossip = member.getPort();
    }

//
//    private void startGossipService(int logLevel, List<GossipMember> gossipMembers, GossipSettings settings, GossipListener listener)
//            throws UnknownHostException, InterruptedException {
//        _gossipService.start();
//
//    }

    public GossipManager getGossipmanager() {
        return _gossipService.get_gossipManager();
    }

//
//    // only for test the storage service
//    private void startStorageService() {
//        this._storageService.start();
//    }
//
//    //only for test the storage system
//    public StorageService getStorageService() {
//        return this._storageService;
//    }

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

    public void shutdown() {
        if (_gossipService != null)
            _gossipService.shutdown();
        if (_storageService != null)
            _storageService.shutdown();
    }

    public void sendToStorage(AppMsg msg){
        _storageService.sendToMe(msg);
    }
    /* callback of gossiping procedure if a node goes UP or DOWN  */
    private void gossipEvent(GossipMember member, GossipState state) {
        switch (state) {
            case UP:
                _storageService.addServer(new Node(member));
                Node.LOGGER.info(this.getIpAddress() + "- UP event, node " + member.getHost() + " added to consistent hasher");
                break;
            case DOWN:
                System.out.println("REMOVED DOWN ");
                _storageService.removeServer(new Node(member));
                Node.LOGGER.info(this.getIpAddress() + "- DOWN event, node " + member.getHost() + " removed from consistent hasher");
                break;
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        return ipAddress.equals(node.ipAddress) && id.equals(node.id);

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

    public int getPortStorage() {
        return this.portStorage;

    }

    public void setPortStorage(int portStorage) {
        this.portStorage = portStorage;

    }

    public StorageService get_storageService() {
        return _storageService;
    }
}