package Node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class SynchGhsController {
    private final SynchGhsService synchGhsService;
    private final SimpMessagingTemplate template;
    private final ThisNodeInfo thisNodeInfo;
    private final GateLock sendingInitialMwoeSearchMessage;
    private final NodeIncrementableRoundSynchronizer nodeIncrementableRoundSynchronizer;
    private final MwoeSearchSynchronizer<MwoeSearchMessage> mwoeSearchSynchronizer;

    private final Object mwoeSearchBarrier;
    private final Runnable mwoeLocalMinWork;

    @Autowired
    public SynchGhsController(
            SynchGhsService synchGhsService,
            SimpMessagingTemplate template,
            @Qualifier("Node/NodeConfigurator/thisNodeInfo")
            ThisNodeInfo thisNodeInfo,
            @Qualifier("Node/SynchGhsConfig/sendingInitialMwoeSearchMessage")
            GateLock sendingInitialMwoeSearchMessage,
            @Qualifier("Node/LeaderElectionConfig/mwoeSearchResponseRoundSynchronizer")
            NodeIncrementableRoundSynchronizer nodeIncrementableRoundSynchronizer,
            @Qualifier("Node/LeaderElectionConfig/mwoeSearchSynchronizer")
            MwoeSearchSynchronizer<MwoeSearchMessage> mwoeSearchSynchronizer
            ){
        this.synchGhsService = synchGhsService;
        this.template = template;
        this.thisNodeInfo = thisNodeInfo;
        this.sendingInitialMwoeSearchMessage = sendingInitialMwoeSearchMessage;
        this.nodeIncrementableRoundSynchronizer = nodeIncrementableRoundSynchronizer;
        this.mwoeSearchSynchronizer = mwoeSearchSynchronizer;

        mwoeSearchBarrier = new Object();

        mwoeLocalMinWork = () -> {
            Queue<MwoeCandidateMessage> mwoeCandidateMessages = nodeIncrementableRoundSynchronizer.getMessagesThisRound();
            List<Edge> candidateEdges = mwoeCandidateMessages.parallelStream()
                    .map(MwoeCandidateMessage::getMwoeCandidate)
                    .collect(Collectors.toList());
            synchGhsService.calcLocalMin(candidateEdges);
        };
    }

    @MessageMapping("/mwoeSearch")
    public void mwoeSearch(MwoeSearchMessage message) {
        //1 if i am not marked and i get a real search message, send out search message
        //2 if i am marked and i get any search message, wait to get one from all neighbors in round, send out null message
        //3 repeat #2 for 3n times
        Runnable mwoeSearchEndOfPhaseWork = () -> {
            //reset isSearched
            synchGhsService.markAsUnSearched();
            //process all real search messages this phase
            Queue<MwoeSearchMessage> mwoeSearchMessages = mwoeSearchSynchronizer.getValidMessagesThisPhase();
            processSearchesForThisPhase(mwoeSearchMessages);
            //reset round number and queues in synchronizers
            mwoeSearchSynchronizer.reset();
        };
        sendingInitialMwoeSearchMessage.enter();
        if(synchGhsService.isSearched()) {
            Runnable markedMwoeSearchWork = () -> {
                sendMwoeSearch(true);
            };
            if(!message.isNullMessage()) {
                //add to list of valid searches received this phase
                mwoeSearchSynchronizer.addValidMessage(message);
            }
            mwoeSearchSynchronizer.incrementProgressAndRunIfReady(message.getRoundNumber(), markedMwoeSearchWork, mwoeSearchEndOfPhaseWork);
        } else {
            Runnable unmarkedMwoeSearchWork = () -> {
                synchGhsService.markAsSearched();
            };
            if(synchGhsService.isFromComponentNode(message.getComponentId())) {
                //must take these actions now instead of end of round
                synchGhsService.mwoeIntraComponentSearch(message.getSourceUID());
                //do not add to list of valid searches since we have already processed it
            } else {
                //add to list of valid searches received this phase
                mwoeSearchSynchronizer.addValidMessage(message);
            }
            mwoeSearchSynchronizer.incrementProgressAndRunIfReady(message.getRoundNumber(), unmarkedMwoeSearchWork, mwoeSearchEndOfPhaseWork);
        }
    }

    @MessageMapping("/mwoeCandidate")
    public void mwoeCandidate(MwoeCandidateMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
               // log.trace("<---received  MwoeCandidate message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received MwoeCandidate message {}", message);
            }
            synchronized (mwoeLocalMinWork) {
                nodeIncrementableRoundSynchronizer.enqueueAndRunIfReady(message, mwoeLocalMinWork);

            }
        }
    }

    @MessageMapping("/mwoeReject")
    public void mwoeReject(MwoeRejectMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
              //  log.trace("<---received  MwoeReject message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received MwoeReject message {}", message);
            }
            synchronized (mwoeLocalMinWork) {
                nodeIncrementableRoundSynchronizer.incrementProgressAndRunIfReady(message.getPhaseNumber(), mwoeLocalMinWork);
            }
        }
    }

    @MessageMapping("/initiateMerge")
    public void initiateMerge(InitiateMergeMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
               // log.trace("<---received  initiateMerge message in if {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received initiateMerge message in else {}", message);
            }
            //TODO implement receiving initiate merge message (relay or whatever)
            Edge selectedMwoeEdge = message.getMwoeEdge();
            if(message.getComponentId()==thisNodeInfo.getComponentId())
            {
                System.out.println("Inside message.getComponentId()==thisNodeInfo.getComponentId() block");
                if(thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid() || thisNodeInfo.getUid() == selectedMwoeEdge.getSecondUid())
                {
                    int otherComponentNode = (thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid()) ?
                            selectedMwoeEdge.getSecondUid() : selectedMwoeEdge.getFirstUid();
                    System.out.println("Other Component Node" + otherComponentNode);
                    List<NodeInfo> neighbors= thisNodeInfo.getNeighbors();
                    for(NodeInfo n: neighbors)
                    {
                       if(n.getUid() == otherComponentNode) {
                           System.out.println("MWOE Edge added because of Parent message");
                           thisNodeInfo.getTreeEdges().add(selectedMwoeEdge);
                       }
                    }
                }
                List<Edge> treeEdgeListSync = thisNodeInfo.getTreeEdges();
                synchronized(treeEdgeListSync) {
                    for (Iterator<Edge> itr = treeEdgeListSync.iterator(); itr.hasNext(); ) {
                        Edge edge = itr.next();
                        int targetUID;
                        if (edge.firstUid != thisNodeInfo.getUid())
                            targetUID = edge.firstUid;
                        else
                            targetUID = edge.secondUid;

                        if (targetUID != message.getSourceUID())
                            sendInitiateMerge(targetUID, selectedMwoeEdge);
                    }
                }

            }
            else if(thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid() || thisNodeInfo.getUid() == selectedMwoeEdge.getSecondUid())
            {
                log.debug("Initiate merge message received from node which is in different component");
                int targetUID = (thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid()) ?
                        selectedMwoeEdge.getSecondUid() : selectedMwoeEdge.getFirstUid();
                if(thisNodeInfo.getUid() == message.getTarget())
                {
                    log.debug("Message is targeted to me, inside  if(thisNodeInfo.getUid() == message.getTarget()) block");

                    if(!GHSUtil.checkList(thisNodeInfo,selectedMwoeEdge)) {
                        log.debug("TreeEdge list does not contain selected MWOE-> {}", selectedMwoeEdge.toString());
                        log.debug("thisnodeinfo object id {}", System.identityHashCode(thisNodeInfo));
                        thisNodeInfo.getTreeEdges().add(selectedMwoeEdge);
                        GHSUtil.printTreeEdgeList(thisNodeInfo.getTreeEdges());
                        //TODO : receive merge requet after new leader is elected
                        if(synchGhsService.getPhaseNumber()!=message.getPhaseNumber())
                        {
                            sendNewLeader(message.getSourceUID());
                        }

                    }

                    else
                    {
                        log.debug("New leader detection logic triggered");
                        int newLeader = Math.max(selectedMwoeEdge.getFirstUid(),selectedMwoeEdge.getSecondUid());
                        System.out.println("New Leader is:" + newLeader);
                        thisNodeInfo.setComponentId(newLeader);
                        synchGhsService.moveToNextPhase();
                        //TODO broadast newLeader in the new merged component
                        List<Edge> treeEdgeListSync = thisNodeInfo.getTreeEdges();
                        synchronized(treeEdgeListSync) {
                            for (Iterator<Edge> itr = treeEdgeListSync.iterator(); itr.hasNext(); ) {
                                Edge edge = itr.next();
                                int sendTo;
                                if (edge.firstUid != thisNodeInfo.getUid())
                                    sendTo = edge.firstUid;
                                else
                                    sendTo = edge.secondUid;

                               /* if (sendTo != message.getSourceUID()) {*/
                                    System.out.println("sending new leader message to "+ sendTo);
                                    sendNewLeader(sendTo);
                                //}
                            }
                        }

                        try{
                            Thread.sleep(10*1000);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        System.out.println("Intiating MWOE Search for next round");
                        if(thisNodeInfo.getUid()==newLeader)
                            sendMwoeSearch(false);
                    }

                }
            }

        }

    }

    @MessageMapping("/newLeader")
    public void newLeader(NewLeaderMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
              //  log.trace("<---received  newLeader message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received newLeader message {}", message);
            }
            if(thisNodeInfo.getComponentId()!=message.newLeaderUID || synchGhsService.getPhaseNumber()!=message.getPhaseNumber()) {
                //update this nodes component id with new leaders UID

                synchGhsService.markAsUnSearched();
                thisNodeInfo.setComponentId(message.getNewLeaderUID());
                synchGhsService.setPhaseNumber(message.getPhaseNumber());
                nodeIncrementableRoundSynchronizer.incrementRoundNumber();
                log.debug("Phase number updated to" + synchGhsService.getPhaseNumber());
                // then relay that message to all its tree edges
                List<Edge> treeEdgeListSync = thisNodeInfo.getTreeEdges();
                synchronized (treeEdgeListSync) {
                    for (Iterator<Edge> itr = treeEdgeListSync.iterator(); itr.hasNext(); ) {
                        Edge edge = itr.next();
                        int targetUID = edge.getFirstUid() != thisNodeInfo.getUid() ? edge.getFirstUid() : edge.getSecondUid();

                        if (targetUID != message.getSourceUID())
                            sendNewLeader(targetUID);
                    }
                }
            }
        }
    }

    public void processSearchesForThisPhase(Queue<MwoeSearchMessage> searchMessages) {
        searchMessages.parallelStream().forEach((message) -> {
            if(synchGhsService.isFromComponentNode(message.getComponentId())) {
                //need to have barrier here to prevent race condition between reading and writing isSearched
                // note that it is also written when phase is transitioned, but we should have guarantee that all mwoeSearch
                // has been completed by then
                synchronized(mwoeSearchBarrier) {
                    if (synchGhsService.isSearched()) {
                        if (log.isDebugEnabled()) {
                            log.debug("<---received another MwoeSearch message {}", message);
                        }
                        sendMwoeReject(message.getSourceUID());
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("<---received MwoeSearch message {}", message);
                }
                synchGhsService.mwoeInterComponentSearch(message.getSourceUID());
            }
        });
    }

    public void sendMwoeSearch(boolean isNullMessage) throws MessagingException {
        MwoeSearchMessage message = new MwoeSearchMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                thisNodeInfo.getComponentId(),
                isNullMessage
                );
        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeSearch message: {}", message);
        }
        template.convertAndSend("/topic/mwoeSearch", message);
        log.trace("MwoeSearch message sent");
    }

    public void sendMwoeCandidate(int targetUid, Edge candidate) throws MessagingException {
        MwoeCandidateMessage message = new MwoeCandidateMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid,
                candidate
        );
        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeCandidate message: {}", message);
        }
        template.convertAndSend("/topic/mwoeCandidate", message);
        log.trace("MwoeCandidate message sent");
    }

    public void sendMwoeReject(int targetUid) throws MessagingException {
        MwoeRejectMessage message = new MwoeRejectMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid
        );

        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeReject message: {}", message);
        }
        template.convertAndSend("/topic/mwoeReject", message);
        log.trace("MwoeReject message sent");
    }

    public void sendInitiateMerge(int targetUid, Edge selectedMwoeEdge) throws MessagingException {
        InitiateMergeMessage message = new InitiateMergeMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid,
                selectedMwoeEdge,
                thisNodeInfo.getComponentId()
        );

        if(log.isDebugEnabled()){
            log.debug("--->sending InitiateMerge message: {}", message);
        }
        template.convertAndSend("/topic/initiateMerge", message);
        log.trace("InitiateMerge message sent");
    }

    public void sendNewLeader(int targetUid) throws MessagingException {
        NewLeaderMessage message = new NewLeaderMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid,
                thisNodeInfo.getComponentId()
        );

        if(log.isDebugEnabled()){
            log.debug("--->sending NewLeaderMessage message: {}", message);
        }
        template.convertAndSend("/topic/newLeader", message);
        log.trace("NewLeader message sent");
    }
}