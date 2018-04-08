/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.network.service.impl;

import io.netty.channel.socket.SocketChannel;
import io.nuls.consensus.constant.PocConsensusConstant;
import io.nuls.core.constant.ErrorCode;
import io.nuls.core.constant.NulsConstant;
import io.nuls.core.context.NulsContext;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.thread.manager.TaskManager;
import io.nuls.core.utils.date.DateUtil;
import io.nuls.core.utils.date.TimeService;
import io.nuls.core.utils.str.StringUtils;
import io.nuls.db.dao.NodeDataService;
import io.nuls.network.constant.NetworkConstant;
import io.nuls.network.entity.Node;
import io.nuls.network.entity.NodeGroup;
import io.nuls.network.entity.NodeTransferTool;
import io.nuls.network.entity.param.AbstractNetworkParam;
import io.nuls.network.service.impl.netty.NioChannelMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author vivi
 * @date 2017/11/21
 */
public class NodesManager implements Runnable {

    private Map<String, NodeGroup> nodeGroups = new ConcurrentHashMap<>();

    private Map<String, Node> disConnectNodes = new ConcurrentHashMap<>();

    private Map<String, Node> canConnectNodes = new ConcurrentHashMap<>();

    private Map<String, Node> connectedNodes = new ConcurrentHashMap<>();

    private ReentrantLock lock = new ReentrantLock();

    private AbstractNetworkParam network;

    private NodeDiscoverHandler discoverHandler;

    private ConnectionManager connectionManager;

    private NodeDataService nodeDao;

    private boolean running;

    private static NodesManager instance = new NodesManager();

    private NodesManager() {

    }

    public static NodesManager getInstance() {
        return instance;
    }

    /**
     * Check if is a consensus node，add consensusNodeGroup
     */
    public void init() {

        // init default NodeGroup
        NodeGroup inNodes = new NodeGroup(NetworkConstant.NETWORK_NODE_IN_GROUP);
        NodeGroup outNodes = new NodeGroup(NetworkConstant.NETWORK_NODE_OUT_GROUP);
        nodeGroups.put(inNodes.getName(), inNodes);
        nodeGroups.put(outNodes.getName(), outNodes);

        boolean isConsensus = NulsContext.MODULES_CONFIG.getCfgValue(PocConsensusConstant.CFG_CONSENSUS_SECTION, PocConsensusConstant.PROPERTY_PARTAKE_PACKING, false);
        if (isConsensus) {
            NodeGroup consensusNodes = new NodeGroup(NetworkConstant.NETWORK_NODE_CONSENSUS_GROUP);
            nodeGroups.put(consensusNodes.getName(), consensusNodes);
        }
    }

    /**
     * get nodes from database
     * connect other nodes
     * running ping/pong thread
     * running node discovery thread
     */
    public void start() {
        List<Node> nodes = discoverHandler.getLocalNodes();
        for (Node node : nodes) {
            disConnectNodes.put(node.getId(), node);
        }
        if (nodes.size() < network.maxOutCount() / 2) {
            for (Node node : getSeedNodes()) {
                disConnectNodes.put(node.getId(), node);
            }
        }
        running = true;
        TaskManager.createAndRunThread(NulsConstant.MODULE_ID_NETWORK, "NetworkNodeManager", this);
        discoverHandler.start();
    }

    public List<Node> getSeedNodes() {
        List<Node> seedNodes = new ArrayList<>();
        for (String ip : network.getSeedIpList()) {
            if (network.getLocalIps().contains(ip)) {
                continue;
            }
            Node node = new Node(network.packetMagic(), Node.OUT, ip, network.port());
            node.setStatus(Node.CLOSE);
            node.setMagicNumber(network.packetMagic());
            node.setSeverPort(node.getPort());
            node.setFailCount(0);

            seedNodes.add(node);
        }
        return seedNodes;
    }

    public List<Node> getAvailableNodes() {
        return new ArrayList<>(connectedNodes.values());
    }

    public List<Node> getConnectNode() {
        List<Node> nodeList = new ArrayList<>();
        for (Node node : disConnectNodes.values()) {
            if (node.isAlive()) {
                nodeList.add(node);
            }
        }
        for (Node node : canConnectNodes.values()) {
            if (node.isAlive()) {
                nodeList.add(node);
            }
        }
        return nodeList;
    }

    public Map<String, Node> getNodes() {
        Map<String, Node> nodeMap = new HashMap<>();
        nodeMap.putAll(disConnectNodes);
        nodeMap.putAll(canConnectNodes);
        nodeMap.putAll(connectedNodes);
        return nodeMap;
    }

    public Node getNode(String nodeId) {
        Node node = disConnectNodes.get(nodeId);
        if (node == null) {
            node = canConnectNodes.get(nodeId);
        }
        if (node == null) {
            node = connectedNodes.get(nodeId);
        }
        return node;
    }

    public void addNode(Node node) {
        lock.lock();
        try {
            if (!disConnectNodes.containsKey(node.getId()) &&
                    !canConnectNodes.containsKey(node.getId()) &&
                    !connectedNodes.containsKey(node.getId())) {

                if (node.getType() == Node.IN) {
                    disConnectNodes.put(node.getId(), node);
                } else {
                    Map<String, Node> nodeMap = getNodes();
                    for (Node n : nodeMap.values()) {
                        if (n.getIp().equals(node.getIp())) {
                            return;
                        }
                    }
                    disConnectNodes.put(node.getId(), node);
                }
            }
        } finally {
            lock.unlock();
        }

    }

    public void removeNode(String nodeId) {
        lock.lock();
        try {
            Node node = disConnectNodes.get(nodeId);
            if (node == null) {
                node = canConnectNodes.get(nodeId);
            }
            if (node == null) {
                node = connectedNodes.get(nodeId);
            }
            if (node == null) {
                getNodeDao().removeNode(nodeId);
                return;
            }

            if (StringUtils.isNotBlank(node.getChannelId())) {
                SocketChannel channel = NioChannelMap.get(node.getChannelId());
                if (channel != null) {
                    channel.close();
                    return;
                }
            }

            for (String groupName : node.getGroupSet()) {
                removeNodeFromGroup(groupName, node.getId());
            }
            //If it is a malicious node, or the node type is "IN",remove it at once
            /**
             * Because the port number is not reliable,
             * the "IN" node will not attempt to connect again after the connection fails, so it should be removed from the map at once
             */
            if (node.getStatus() == Node.BAD || node.getType() == Node.IN) {
                connectedNodes.remove(nodeId);
                disConnectNodes.remove(nodeId);
                connectedNodes.remove(nodeId);
                getNodeDao().removeNode(nodeId);
                return;
            }

            node.destroy();
            if (connectedNodes.containsKey(nodeId)) {
                connectedNodes.remove(nodeId);
            }
            if (node.isCanConnect()) {
                if (!canConnectNodes.containsKey(nodeId)) {
                    for (Node n : canConnectNodes.values()) {
                        if (node.getIp().equals(n.getIp())) {
                            node.setFailCount(node.getFailCount() + 1);
                            node.setLastFailTime(TimeService.currentTimeMillis() + DateUtil.MINUTE_TIME * node.getFailCount());
                            return;
                        }
                    }
                    node.setLastFailTime(TimeService.currentTimeMillis() + DateUtil.MINUTE_TIME * 10);
                    node.setType(Node.OUT);
                    canConnectNodes.put(nodeId, node);
                    disConnectNodes.remove(nodeId);
                }

            } else if (node.getFailCount() >= 20) {
                if (disConnectNodes.containsKey(nodeId)) {
                    disConnectNodes.remove(nodeId);
                }
                getNodeDao().removeNode(nodeId);
            } else {
                node.setFailCount(node.getFailCount() + 1);
                node.setLastFailTime(TimeService.currentTimeMillis() + DateUtil.MINUTE_TIME * node.getFailCount());
                if (!disConnectNodes.containsKey(nodeId)) {
                    disConnectNodes.put(nodeId, node);
                    canConnectNodes.remove(nodeId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public void blackNode(String nodeId, int status) {
//        if (nodes.containsKey(nodeId)) {
//            Node node = nodes.get(nodeId);
//            node.setStatus(status);
//            node.setFailCount(20);
//            getNodeDao().removeNode(NodeTransferTool.toPojo(node));
//
//            removeNode(node.getId(), null);
//        }
    }

    public boolean addNodeToGroup(String groupName, Node node) {
        if (!nodeGroups.containsKey(groupName)) {
            throw new NulsRuntimeException(ErrorCode.NET_NODE_GROUP_NOT_FOUND);
        }
        NodeGroup group = nodeGroups.get(groupName);
        if (groupName.equals(NetworkConstant.NETWORK_NODE_OUT_GROUP) &&
                group.size() >= network.maxOutCount()) {
            return false;
        }
        if (groupName.equals(NetworkConstant.NETWORK_NODE_IN_GROUP) &&
                group.size() >= network.maxInCount()) {
            return false;
        }
        node.getGroupSet().add(group.getName());
        group.addNode(node);
        return true;
    }

    public void removeNodeFromGroup(String groupName, String nodeId) {
        if (!nodeGroups.containsKey(groupName)) {
            return;
        }
        nodeGroups.get(groupName).removeNode(nodeId);
    }

    public void handshakeNode(Node node) {
        boolean success = false;
        if (node.getType() == Node.IN) {
            success = addNodeToGroup(NetworkConstant.NETWORK_NODE_IN_GROUP, node);
        } else if (node.getType() == Node.OUT) {
            success = addNodeToGroup(NetworkConstant.NETWORK_NODE_OUT_GROUP, node);
        }
        node.setFailCount(0);
        if (!success) {
            node.setCanConnect(true);
            removeNode(node.getId());
        } else {
            node.setStatus(Node.HANDSHAKE);
            if (disConnectNodes.containsKey(node.getId())) {
                disConnectNodes.remove(node.getId());
            }
            if (canConnectNodes.containsKey(node.getId())) {
                canConnectNodes.remove(node.getId());
            }
            connectedNodes.put(node.getId(), node);
            getNodeDao().saveChange(NodeTransferTool.toPojo(node));
        }
    }


    private boolean isSeedNode(String ip) {
        return network.getSeedIpList().contains(ip);
    }

    /**
     * check the nodes when closed try to connect other one
     */
    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

        while (running) {

            for (Node node : connectedNodes.values()) {
                System.out.println("------------------------" + node.getId() + ",type:" + node.getType());
            }

            // check the connectedNodes, if it is empty, try to connect seed node,
            // if connectedNode's count enough, closing the connection with the seed node
            if (connectedNodes.isEmpty() && canConnectNodes.isEmpty()) {
                List<Node> nodes = getSeedNodes();
                for (Node node : nodes) {
                    if (!disConnectNodes.containsKey(node.getId())) {
                        disConnectNodes.put(node.getId(), node);
                    }
                }
            } else if (connectedNodes.size() >= network.maxOutCount()) {
                removeSeedNode();
            }

            //unConnectNodes untime try to connect
            for (Node node : disConnectNodes.values()) {
                if (node.getType() == Node.OUT && node.getStatus() == Node.CLOSE) {

                    if (node.getLastFailTime() < TimeService.currentTimeMillis() || isSeedNode(node.getIp())) {
                        connectionManager.connectionNode(node);
                    }
                }
            }
            //canConnectNodes untime try to connect
            int size = network.maxOutCount() - nodeGroups.get(NetworkConstant.NETWORK_NODE_OUT_GROUP).size();
            for (Node node : canConnectNodes.values()) {
                if (size > 0) {
                    connectionManager.connectionNode(node);
                    size--;
                } else if (node.getLastFailTime() < TimeService.currentTimeMillis() && node.getStatus() == Node.CLOSE) {
                    connectionManager.connectionNode(node);
                }
            }
            size = network.maxOutCount() - nodeGroups.get(NetworkConstant.NETWORK_NODE_OUT_GROUP).size();
            if (size > 0) {
                discoverHandler.findOtherNode(size);
            }

            try {
                Thread.sleep(3500);
            } catch (InterruptedException e) {

            }
        }
    }

    private void removeSeedNode() {
//        Collection<Node> nodes = connectedNodes.values();
//        for (String ip : network.getSeedIpList()) {
//            for (Node n : nodes) {
//                if (n.getIp().equals(ip)) {
//                    removeNode(n.getId());
//                }
//            }
//        }
        String id = "192.168.1.204:8003";
        removeNode(id);
    }

    public NodeGroup getNodeGroup(String groupName) {
        return nodeGroups.get(groupName);
    }

    public void setNetwork(AbstractNetworkParam network) {
        this.network = network;
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void setDiscoverHandler(NodeDiscoverHandler discoverHandler) {
        this.discoverHandler = discoverHandler;
    }

    private NodeDataService getNodeDao() {
        if (nodeDao == null) {
            nodeDao = NulsContext.getServiceBean(NodeDataService.class);
        }
        return nodeDao;
    }
}
