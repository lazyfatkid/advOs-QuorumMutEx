package Node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class SynchGhsController {
    private final SynchGhsService synchGhsService;
    private final SimpMessagingTemplate template;
    private final ThisNodeInfo thisNodeInfo;
    private final GateLock sendingInitialMwoeSearchMessage;
    private final Object mwoeSearchBarrier;

    @Autowired
    public SynchGhsController(
            SynchGhsService synchGhsService,
            SimpMessagingTemplate template,
            @Qualifier("Node/NodeConfigurator/thisNodeInfo")
            ThisNodeInfo thisNodeInfo,
            @Qualifier("Node/SynchGhsConfig/sendingInitialMwoeSearchMessage")
            GateLock sendingInitialMwoeSearchMessage
            ){
        this.synchGhsService = synchGhsService;
        this.template = template;
        this.thisNodeInfo = thisNodeInfo;
        this.sendingInitialMwoeSearchMessage = sendingInitialMwoeSearchMessage;

        mwoeSearchBarrier = new Object();
    }

    @MessageMapping("/mwoeSearch")
    public void mwoeSearch(MwoeSearchMessage message) {
        /*TODO buffer messages from other phases and only process them when we receive notification from leader that we
        are going into that phase*/
        if(synchGhsService.isFromComponentNode(message.getComponentId())) {
            sendingInitialMwoeSearchMessage.enter();
            //need to have barrier here to prevent race condition between reading and writing isSearched
            // note that it is also written when phase is transitioned, but we should have guarantee that all mwoeSearch
            // has been completed by then
            synchronized(mwoeSearchBarrier) {
                if (synchGhsService.isSearched()) {
                    if (log.isDebugEnabled()) {
                        log.debug("<---received another MwoeSearch message {}", message);
                    }
                    //TODO send targeted searchRejectMessage to sender
                } else {
                    synchGhsService.mwoeIntraComponentSearch(message.getSourceUID(), message.getComponentId());
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received MwoeSearch message {}", message);
            }
            synchGhsService.mwoeInterComponentSearch(message.getSourceUID(), message.getComponentId());
        }
    }


    @MessageMapping("/mwoeResponse")
    public void mwoeResponse(MwoeResponseMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
                log.trace("<---received  MwoeResponse message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received MwoeResponse message {}", message);
            }
            //TODO buffer localMinMessages AND mwoeResonseMessages until we have one from EVERY node that isn't our "parent"
            //TODO implement
        }
    }

    public void sendMwoeSearch() throws MessagingException {
        MwoeSearchMessage message = new MwoeSearchMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                thisNodeInfo.getComponentId()
                );
        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeSearch message: {}", message);
        }
        template.convertAndSend("/topic/mwoeSearch", message);
        log.trace("leader election message sent");
    }

    public void sendMwoeResponse(int targetUid) throws MessagingException {
        MwoeResponseMessage message = new MwoeResponseMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid
        );
        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeResponse message: {}", message);
        }
        template.convertAndSend("/topic/mwoeResponse", message);
        log.trace("MwoeSearch message sent");
    }

    public void sendMwoeReject(int targetUid) throws MessagingException {
        MwoeRejectMessage message = new MwoeRejectMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid
        );

        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeResponse message: {}", message);
        }
        template.convertAndSend("/topic/mwoeResponse", message);
        log.trace("MwoeSearch message sent");
    }
}