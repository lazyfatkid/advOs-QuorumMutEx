package Node;

import java.util.*;

public final class ThisNodeInfo extends NodeInfo{
    private final List<NodeInfo> quorum;
    private final int totalNumberOfNodes;
    private final int numberOfRequests;

    ThisNodeInfo(
            int uid,
            int totalNumberOfNodes,
            String hostName,
            int port,
            int numberOfRequests
            ) {
        super(uid, hostName, port);
        quorum = new ArrayList<>();
        this.totalNumberOfNodes = totalNumberOfNodes;
        this.numberOfRequests = numberOfRequests;
    }

    public boolean addNeighbor(NodeInfo neighbor){
        return quorum.add(neighbor);
    }

    public List<NodeInfo> getQuorum() {
        return quorum;
    }

    public int getTotalNumberOfNodes() {
        return totalNumberOfNodes;
    }

    public int getNumberOfRequests() {
        return numberOfRequests;
    }



}
