package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.RequestObjects.MemberListRequest;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by luke on 2016/04/28.
 */
@RestController
@RequestMapping(value = "/ajax")
public class AjaxController {

    private static final Logger log = LoggerFactory.getLogger(AjaxController.class);

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private LogBookBroker logBookBroker;

    @RequestMapping(value = "/members/list", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> retrieveParentMembers(@RequestBody MemberListRequest listRequest) {

        MemberPicker memberPicker;
        final JpaEntityType type = listRequest.getParentEntityType();
        final String parentUid = listRequest.getParentUid();
        final boolean selected = listRequest.isSelectedByDefault();

        if (JpaEntityType.GROUP.equals(type)) {
            memberPicker = new MemberPicker(groupBroker.load(parentUid), selected);
        } else if (JpaEntityType.MEETING.equals(type) || JpaEntityType.VOTE.equals(type)) {
            memberPicker = new MemberPicker(eventBroker.load(parentUid), selected);
        } else if (JpaEntityType.LOGBOOK.equals(type)) {
            memberPicker = new MemberPicker(logBookBroker.load(parentUid), selected);
        } else {
            // todo: look into repetition of http status in this
            ResponseWrapper error = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.INVALID_ENTITY_TYPE, RestStatus.FAILURE);
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        ResponseWrapper body = new GenericResponseWrapper(HttpStatus.FOUND, RestMessage.PARENT_MEMBERS,
                                                                     RestStatus.SUCCESS, memberPicker);
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> logIncomingRequest(@RequestBody MemberListRequest listRequest, HttpServletRequest request) {
        log.info("Received a request! It's this: {}", listRequest.toString());
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.FOUND, RestMessage.PARENT_MEMBERS, RestStatus.SUCCESS),
                                    HttpStatus.OK);
    }

}