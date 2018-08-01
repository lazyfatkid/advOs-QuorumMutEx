package Node;

import java.util.*;

public class QuorumMutExInfo {
    private int scalarClock;
    private final PriorityQueue<CsRequest> waitingRequestQueue;
    private final Queue<ReceivedInquiry> inquiriesPendingFailed;
    private final Queue<ReceivedInquiry> inquiriesPendingGrant;
    private CsRequest activeRequest;
    private boolean isActive;
    private boolean isInquireSent;
    private boolean isFailedReceived;
    private Map<UUID, Set<Integer>> grantsReceived;

    public QuorumMutExInfo() {
        this.scalarClock = 0;
        waitingRequestQueue = new PriorityQueue<>();
        inquiriesPendingFailed = new LinkedList<ReceivedInquiry>();
        inquiriesPendingGrant = new LinkedList<ReceivedInquiry>();
        isActive = false;
        isInquireSent = false;
        isFailedReceived = false;
        grantsReceived = new HashMap<>();
    }

    public int getScalarClock () {
                               return scalarClock;
                                                  }

    public void setScalarClock ( int scalarClock){
        this.scalarClock = scalarClock;
    }

    public void incrementScalarClock () {
        scalarClock++;
    }

    public PriorityQueue<CsRequest> getWaitingRequestQueue() {
        return waitingRequestQueue;
    }

    public void setActiveRequest(CsRequest activeRequest) {
        this.activeRequest = activeRequest;
    }

    public CsRequest getActiveRequest() {
        return activeRequest;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isInquireSent() {
        return isInquireSent;
    }

    public void setInquireSent(boolean inquireSent) {
        isInquireSent = inquireSent;
    }

    public Queue<ReceivedInquiry> getInquiriesPendingFailed() {
        return inquiriesPendingFailed;
    }

    public Queue<ReceivedInquiry> getInquiriesPendingGrant() {
        return inquiriesPendingGrant;
    }

    public boolean removeInquiryPendingGrant(UUID requestId) {
        return  inquiriesPendingGrant.removeIf((inquiry -> {
            return inquiry.getRequestId().equals(requestId);
        }));
    }

    public boolean isFailedReceived() {
        return isFailedReceived;
    }

    public void setFailedReceived(boolean failedReceived) {
        isFailedReceived = failedReceived;
    }

    public int getNumGrantsReceived(UUID requestId) {
        int numGrantsReceived = 0;
        if(grantsReceived.containsKey(requestId)) {
            Set<Integer> grantsReceivedForRequest = grantsReceived.get(requestId);
            numGrantsReceived = grantsReceivedForRequest.size();
        }
        return numGrantsReceived;
    }

    public boolean isGrantReceived(UUID requestId, int grantorUid) {
        boolean isGrantReceived = false;
        if(grantsReceived.containsKey(requestId)) {
            Set<Integer> grantsReceivedForRequest = grantsReceived.get(requestId);
            if(grantsReceivedForRequest.contains(grantorUid)) {
                isGrantReceived = true;
            }
        }
        return isGrantReceived;
    }

    public void addGrantReceived(UUID requestId, int grantorUid) {
        Set<Integer> grantsReceivedForRequest;
        if(grantsReceived.containsKey(requestId)) {
            grantsReceivedForRequest = grantsReceived.get(requestId);
        } else {
            grantsReceivedForRequest = new HashSet<Integer>(1);
        }
        grantsReceivedForRequest.add(grantorUid);
        grantsReceived.put(requestId, grantsReceivedForRequest);
    }

    public void removeGrantReceived(UUID requestId, int grantorUid) {
        Set<Integer> grantsReceivedForRequest = grantsReceived.get(requestId);
        grantsReceivedForRequest.remove(grantorUid);
    }

    public void resetGrantsReceived(UUID requestId) {
        grantsReceived.remove(requestId);
    }
}
