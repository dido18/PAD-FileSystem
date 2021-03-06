package com.dido.pad.cli.client;

import com.dido.pad.Helper;
import com.dido.pad.Node;
import com.dido.pad.cli.ClientHelper;
import com.dido.pad.hashing.DefaultFunctions;
import com.dido.pad.hashing.Hasher;
import com.dido.pad.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.code.gossip.GossipMember;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by dido-ubuntu on 07/03/16.
 */
public class Cli {


    public static final Logger LOGGER = Logger.getLogger(Cli.class);

    private Hasher<Node> cHasher;

    private DatagramSocket udpServer;
    private AtomicBoolean keepRunning;

    private Client client;

    private BufferedReader bufferReader;

   public Cli(Client client, List<GossipMember> seedNodes) {

        bufferReader = new BufferedReader(new InputStreamReader(System.in));

        this.cHasher = new Hasher<>(DefaultFunctions::SHA1, DefaultFunctions::BytesConverter);
        this.client = client;

        // ADD seed nodes to the node storage service
        for (GossipMember member : seedNodes) {
            Node n = new Node(member);
                cHasher.addServer(n);
        }

        keepRunning = new AtomicBoolean(true);
        try {
            SocketAddress sAddress = new InetSocketAddress(client.getIpAddress(), Helper.STORAGE_PORT);
            LOGGER.info(client.getIpAddress() + "- initialized on portStorage " + Helper.STORAGE_PORT);
            udpServer = new DatagramSocket(sAddress);
            udpServer.setSoTimeout(ClientHelper.TIMEOUT_TO_STORAGE_NODE);
        } catch (SocketException e) {
            LOGGER.error(this.client.getIpAddress() + " - at init-" + e);
            keepRunning.set(false);
            udpServer = null;
        }

    }

    public void removeServer(Node n) {
        cHasher.removeServer(n);
    }


    public Hasher<Node> getcHasher() {
        return cHasher;
    }

    public void addServer(Node n) {
       // if(!cHasher.containsNode(n)) {
            cHasher.addServer(n);
        //    return true;
        //}else
        //    return false;
    }

    //update nodes of the storage service received from the selected node.
    public void updateNodes(ArrayList<Node> nodes){
        for (Node n : nodes) {
            if (!cHasher.containsNode(n))
                cHasher.addServer(n);
        }
        for (Node myNode : cHasher.getAllNodes()){
            if(!nodes.contains(myNode))
                cHasher.removeServer(myNode);
        }
    }

    public void runCli() {
        String help = " usage:\n " +
                "\t put key value   : put the <key:value> into the system \n"  +
                "\t get key         : retrieve the value associated to te key \n"+
                "\t list ipAddress  : lists the pairs <key:value> into the database of ipAddress \n"+
                "\t rm key          : remove the key valu in the master and in the backups \n"+
                "\t force key value ip : sent to the specific ip the key value pair\n"+
                "\t nodes           : shows the nodes active inthe system \n";

        System.out.print("\nInsert a command (h for usage message)");
        String input = null;
        while(true){

            System.out.print("\n>> ");

            try {
                input = bufferReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if( input !=null) {
                String[] cmds = input.split("\\s+"); //splits  white spaces

                switch (cmds[0]) {
                    case ("get"):
                        String key = cmds[1];
                        sendGetAndWait(key);
                        break;
                    case ("put"):
                        String k = cmds[1];
                        String v = cmds[2];
                        sendPutAndWait(k, v);
                        break;
                    case ("list"):
                        String ip = cmds[1];
                        sendListAndWait(ip);
                        break;
                    case ("rm"):
                        String Rmkey = cmds[1];
                        sendRmAndWait(Rmkey);
                        break;
                    case ("nodes"):
                        System.out.print(getcHasher().getAllNodes());
                        break;
                    case ("force"):
                        String kk = cmds[1];
                        String vv = cmds[2];
                        String Ip = cmds[3];
                        sendForce(kk, vv, Ip);
                        ;
                        break;
                    case ("h"):
                        System.out.print(help);
                        break;

                }
            }
        }
    }

    private void sendRmAndWait(String rmkey) {
        Node n = getcHasher().getServerForData(rmkey);
        RequestAppMsg msgRm = new RequestAppMsg<>(Msg.OP.RM,rmkey,"");
        msgRm.setIpSender(client.getIpAddress());
        String ip = n.getIpAddress();

        try {
            InetAddress address = InetAddress.getByName(ip);

            byte[] buf = Helper.fromAppMsgtoByte(msgRm);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, Helper.STORAGE_PORT);
            udpServer.send(packet);
            LOGGER.info(client.getIpAddress() + "- Sent RM to: " +ip);

            byte[] buff = new byte[udpServer.getReceiveBufferSize()];
            DatagramPacket p = new DatagramPacket(buff, buff.length);
            udpServer.receive(p);

            int packet_length = 0;
            for (int i = 0; i < 4; i++) {
                int shift = (4 - 1 - i) * 8;
                packet_length += (buff[i] & 0x000000FF) << shift;
            }

            byte[] json_bytes = new byte[packet_length];
            System.arraycopy(buff, 4, json_bytes, 0, packet_length);
            String receivedMessage = new String(json_bytes);

            ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());
            ReplyAppMsg msgReceived = mapper.readValue(receivedMessage, ReplyAppMsg.class);
            manageAppReply(msgReceived);

        } catch (IOException e) {
            LOGGER.info("RM command "+e.getMessage());
            //e.printStackTrace();
        }

    }

    private void sendForce(String kk, String vv, String ip) {

        for (Node node:getcHasher().getAllNodes()) {
            if (node.getIpAddress().equals(ip)) {
                RequestAppMsg msg = new RequestAppMsg<>(Msg.OP.PUT, kk, vv);
                msg.setIpSender(ip);
                node.sendToStorage(msg);
                LOGGER.info(ip + "- sent FORCE to " + node.getIpAddress());
                return;
            }
        }
    }

    private void sendListAndWait(String ip) {
        RequestAppMsg msg = new RequestAppMsg<>(Msg.OP.LIST, "", "");
        msg.setIpSender(client.getIpAddress());

        try {
            InetAddress address = InetAddress.getByName(ip);

            byte[] buf = Helper.fromAppMsgtoByte(msg);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, Helper.STORAGE_PORT);
            udpServer.send(packet);
            LOGGER.debug(client.getIpAddress() + "- Sent LIST to: " +ip);

            byte[] buff = new byte[udpServer.getReceiveBufferSize()];
            DatagramPacket p = new DatagramPacket(buff, buff.length);
            udpServer.receive(p);

            int packet_length = 0;
            for (int i = 0; i < 4; i++) {
                int shift = (4 - 1 - i) * 8;
                packet_length += (buff[i] & 0x000000FF) << shift;
            }

            byte[] json_bytes = new byte[packet_length];
            System.arraycopy(buff, 4, json_bytes, 0, packet_length);
            String receivedMessage = new String(json_bytes);

            ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());
            ReplyAppMsg msgReceived = mapper.readValue(receivedMessage, ReplyAppMsg.class);
            manageAppReply(msgReceived);

        } catch (IOException e) {
            LOGGER.info("LIST command "+e.getMessage());
            //e.printStackTrace();
        }
    }

    private void sendPutAndWait(String key, String value) {
        Node n = getcHasher().getServerForData(key);
        String ip = n.getIpAddress();
        RequestAppMsg msg = new RequestAppMsg<>(Msg.OP.PUT, key, value);
        msg.setIpSender(client.getIpAddress());

        try {
            InetAddress address = InetAddress.getByName(ip);

            byte[] buf = Helper.fromAppMsgtoByte(msg);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, Helper.STORAGE_PORT);
            udpServer.send(packet);
            LOGGER.info(client.getIpAddress() + "- Sent PUT to: " + n.getIpAddress());

            byte[] buff = new byte[udpServer.getReceiveBufferSize()];
            DatagramPacket p = new DatagramPacket(buff, buff.length);
            LOGGER.debug(client.getIpAddress() + " - waiting PUT response...");
            udpServer.receive(p);

            int packet_length = 0;
            for (int i = 0; i < 4; i++) {
                int shift = (4 - 1 - i) * 8;
                packet_length += (buff[i] & 0x000000FF) << shift;
            }

            byte[] json_bytes = new byte[packet_length];
            System.arraycopy(buff, 4, json_bytes, 0, packet_length);
            String receivedMessage = new String(json_bytes);

            ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());
            ReplyAppMsg msgReceived = mapper.readValue(receivedMessage, ReplyAppMsg.class);
            manageAppReply(msgReceived);

        } catch (IOException e) {
            LOGGER.info("PUT command "+e.getMessage());
        }

    }

    private void sendGetAndWait(String key)  {
        Node n = getcHasher().getServerForData(key);
        String ip = n.getIpAddress();
        RequestAppMsg msg = new RequestAppMsg<>(Msg.OP.GET, key, "");
        msg.setIpSender(client.getIpAddress());

        try {
            InetAddress  address = InetAddress.getByName(ip);

            byte[] buf = Helper.fromAppMsgtoByte(msg);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, Helper.STORAGE_PORT);
            udpServer.send(packet);
            LOGGER.debug(client.getIpAddress() + "- sent GET to: " + n.getIpAddress());

            byte[] buff = new byte[udpServer.getReceiveBufferSize()];
            DatagramPacket p = new DatagramPacket(buff, buff.length);
            LOGGER.debug(client.getIpAddress() + " - waiting GET response...");

            udpServer.receive(p);

            int packet_length = 0;
            for (int i = 0; i < 4; i++) {
                int shift = (4 - 1 - i) * 8;
                packet_length += (buff[i] & 0x000000FF) << shift;
            }

            byte[] json_bytes = new byte[packet_length];
            System.arraycopy(buff, 4, json_bytes, 0, packet_length);
            String receivedMessage = new String(json_bytes);

            ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());
            Msg msgReceived = mapper.readValue(receivedMessage, Msg.class);
            // reply App Message
            if(msgReceived instanceof ReplyAppMsg)
                manageAppReply((ReplyAppMsg)msgReceived);
            // request conflict message received
            else if(msgReceived instanceof  RequestConflictMsg){
                manageConflictMessage((RequestConflictMsg) msgReceived);
            }

        } catch (IOException e) {
            LOGGER.info("GET command "+e.getMessage());

        }

    }


    private void manageConflictMessage(RequestConflictMsg msg) {
        switch (msg.getType()) {
            case REQUEST:
                System.out.println("Insert a number to select the right version :\n"+msg.getSelection());
                System.out.print(">> ");
                int selection;
                try {
                    selection = Integer.parseInt(bufferReader.readLine());
                    ReplyConflictMsg msgRely = new ReplyConflictMsg(Msg.TYPE.REPLY, Msg.OP.OK, selection);
                    send(msg.getIpSender(), Helper.CONFLICT_LISTEN_PORT, msgRely);
                    LOGGER.info("SENT selection: " + selection+ " to: " + msg.getIpSender());//on port "+msg.getPortSender() );
                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
        }
    }


    private void manageAppReply(ReplyAppMsg msg) {
        switch (msg.getOperation()) {
            case OK:
                LOGGER.info(client.getIpAddress() + " - REPLY  OK  from " + msg.getIpSender()+" " + msg.getMsg());
                break;
            case ERR:
                LOGGER.info(client.getIpAddress() + " - REPLY  ERR  from " +msg.getIpSender() + msg.getMsg());
                break;
        }
    }

    protected void send(String destIp, int destPort, Msg msg) {
        try {

            InetAddress address = InetAddress.getByName(destIp);

            if (msg.getIpSender() == null)
                msg.setIpSender(client.getIpAddress());
            byte[] buf = Helper.fromAppMsgtoByte(msg);

            DatagramSocket dsocket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, destPort);
            dsocket.send(packet);
            dsocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        LOGGER.info(this.client.getIpAddress() + "-  Storage service has been shutdown...");
        udpServer.close();
    }

    public Node getRandomNode() throws IllegalArgumentException{
        ArrayList<Node> nodes = cHasher.getAllNodes();
        Random rand = new Random();
        int random;
        if(nodes.size()>0) {
            random = rand.nextInt(nodes.size());
        }
        else {
            throw  new IllegalArgumentException("NO nodes  in the system");
        }
        return nodes.get(random);
    }

}
