package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventRequestBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.backVoteUrl;
import static za.org.grassroot.webapp.util.USSDUrlUtil.entityUidUrlSuffix;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveVoteMenu;

/**
 * Created by luke on 2015/10/28.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDVoteController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDVoteController.class);

    @Autowired
    EventRequestBroker eventRequestBroker;

    @Autowired
    EventLogManagementService eventLogManager;

    @Autowired
    private USSDEventUtil eventUtil;

    private static final String path = homePath + voteMenus;
    private static final USSDSection thisSection = USSDSection.VOTES;

    // for stubbing with Mockito
    public void setEventUtil(USSDEventUtil eventUtil) {
        this.eventUtil = eventUtil;
    }

    /*
    First menu asks user to select a group. Until we have a "snap voting" functionality worked out, this requires
    the user to have a group already set up (i.e., is different from meeting menu, which allows within-flow group creation
    Major todo: add menus to see status of vote while in progress, and possibly trigger reminder
     */
    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request votingStart(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        int hasVotesToView = eventManager.userHasEventsToView(user, EventType.VOTE);
        log.info("Checked for votes to view ... got integer: " + hasVotesToView);
        USSDMenu menu;

        if (hasVotesToView >= -1) {
            menu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, user));
            menu.addMenuOption(voteMenus + "new", getMessage(thisSection, startMenu, optionsKey + "new", user));
            if (hasVotesToView >= 0)
                menu.addMenuOption(voteMenus + "open", getMessage(thisSection, startMenu, optionsKey + "open", user));
            if (hasVotesToView <= 0)
                menu.addMenuOption(voteMenus + "old", getMessage(thisSection, startMenu, optionsKey + "old", user));
        } else {
            String groupsExistPrompt = getMessage(thisSection, "group", promptKey, user);
            String groupsDontExistPrompt = getMessage(thisSection, "group", promptKey + "-nogroup", user);
            menu = ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, groupsExistPrompt, groupsDontExistPrompt,
                    "issue", groupMenus + "create", null);
        }

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "new")
    @ResponseBody
    public Request newVote(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, voteMenus + "new");
        String groupsExistPrompt = getMessage(thisSection, "group", promptKey, user);
        String groupsDontExistPrompt = getMessage(thisSection, "group", promptKey + "-nogroup", user);
        return menuBuilder(ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, groupsExistPrompt, groupsDontExistPrompt,
                "issue", groupMenus + "create", null));
    }

    /*
    Second menu asks the user to enter the issue that will be voted upon
    todo: some form of length restriction / checking
     */
    @RequestMapping(value = path + "issue")
    @ResponseBody
    public Request votingIssue(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupUidParam, required = false) String groupUid,
                               @RequestParam(value = entityUidParam, required = false) String requestUid,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                               @RequestParam(value = revisingFlag, required = false) boolean revising) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);

        if (requestUid == null) {
            VoteRequest voteRequest = eventRequestBroker.createEmptyVoteRequest(user.getUid(), groupUid);
            requestUid = voteRequest.getUid();
        }

        cacheManager.putUssdMenuForUser(inputNumber, saveVoteMenu("issue", requestUid));

        String nextUrl = (!revising) ? voteMenus + "time" + entityUidUrlSuffix + requestUid :
                voteMenus + "confirm" + entityUidUrlSuffix + requestUid + "&field=issue";

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "issue", promptKey, user), nextUrl);
        return menuBuilder(menu);

    }

    /*
    Third menu asks the user when the vote will close. Options are "instant vote", i.e., 5 minutes, versus "one day",
    versus "custom".
     */
    @RequestMapping(value = path + "time")
    @ResponseBody
    public Request votingTime(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = entityUidParam) String requestUid,
                              @RequestParam(value = userInputParam) String issue,
                              @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                              @RequestParam(value = revisingFlag, required = false) boolean revising) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("time", requestUid));

        if (!interrupted && !revising) eventRequestBroker.updateName(user.getUid(), requestUid, issue);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey, user));

        String nextUrl = voteMenus + "confirm" + entityUidUrlSuffix + requestUid + "&field=standard&time=";
        String optionKey = voteKey + ".time." + optionsKey;

        menu.addMenuOption(nextUrl + "instant", getMessage(optionKey + "instant", user));
        menu.addMenuOption(nextUrl + "hour", getMessage(optionKey + "hour", user));
        menu.addMenuOption(nextUrl + "day", getMessage(optionKey + "day", user));
        menu.addMenuOption(nextUrl + "week", getMessage(optionKey + "week", user));
        menu.addMenuOption(voteMenus + "time_custom" + entityUidUrlSuffix + requestUid, getMessage(optionKey + "custom", user));

        return menuBuilder(menu);
    }

    /*
    Optional menu if user wants to enter a custom expiry time
     */
    @RequestMapping(value = path + "time_custom")
    @ResponseBody
    public Request customVotingTime(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = entityUidParam) String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("time_custom", requestUid));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey + "-custom", user));
        menu.setFreeText(true);
        menu.setNextURI(voteMenus + "confirm" + entityUidUrlSuffix + requestUid + "&field=custom");

        return menuBuilder(menu);

    }

    /*
    Final menu asks for confirmation, then sends out
    major todo: shift the time strings into messages (and generally do i18n for this)
     */
    @RequestMapping(value = path + "confirm")
    @ResponseBody
    public Request voteConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = entityUidParam) String requestUid,
                               @RequestParam(value = userInputParam) String userInput,
                               @RequestParam(value = "time", required = false) String time,
                               @RequestParam(value = "field", required = false) String field,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("confirm", requestUid));

        String[] promptFields;

        if (!interrupted) {
            switch (field) {
                case "standard":
                    promptFields = setStandardTime(requestUid, time, user);
                    break;
                case "custom":
                    promptFields = setCustomTime(requestUid, userInput, user);
                    break;
                case "issue":
                    promptFields = adjustSubject(requestUid, userInput, user);
                    break;
                default:
                    promptFields = new String[]{"Error!", "Error occurred!"};
                    break;
            }
        } else {
            EventRequest vote = eventRequestBroker.load(requestUid);
            promptFields = new String[]{vote.getName(), "at " + vote.getEventDateTimeAtSAST().format(dateTimeFormat)};
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "confirm", promptKey, promptFields, user));
        menu.addMenuOption(voteMenus + "send" + entityUidUrlSuffix + requestUid,
                           getMessage(thisSection, "confirm", optionsKey + "yes", user));
        menu.addMenuOption(backVoteUrl("issue", requestUid), getMessage(thisSection, "confirm", optionsKey + "topic", user));
        menu.addMenuOption(backVoteUrl("time", requestUid), getMessage(thisSection, "confirm", optionsKey + "time", user));

        return menuBuilder(menu);
    }

    /*
    Send out and confirm it has been sent
     */
    @RequestMapping(value = path + "send")
    @ResponseBody
    public Request voteSend(@RequestParam(value = phoneNumber) String inputNumber,
                            @RequestParam(value = entityUidParam) String requestUid) throws Exception {

        User user = userManager.findByInputNumber(inputNumber, null);
        String createdUid = eventRequestBroker.finish(user.getUid(), requestUid, true);

        Event vote = eventBroker.load(createdUid);
        log.info("Vote details confirmed! Closing date and time: " + vote.getEventDateTimeAtSAST().format(dateTimeFormat));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "send", promptKey, user), optionsHomeExit(user));

        return menuBuilder(menu);
    }

    /**
     * SECTION: menus to view open & old votes
     */

    @RequestMapping(value = path + "open")
    @ResponseBody
    public Request viewOpenVotes(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        // todo: consider doing a save and return
        User user = userManager.findByInputNumber(inputNumber, voteMenus + "open");
        String prompt = getMessage(thisSection, "open", promptKey, user);
        return menuBuilder(eventUtil.listUpcomingEvents(user, thisSection, prompt, "details?back=open", false, null, null));
    }

    @RequestMapping(value = path + "old")
    @ResponseBody
    public Request viewOldVotes(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, voteMenus + "old");
        String prompt = getMessage(thisSection, "old", promptKey, user);
        return menuBuilder(eventUtil.listPriorEvents(user, thisSection, prompt, "details?back=old", true));
    }

    @RequestMapping(value = path + "details")
    @ResponseBody
    public Request viewVoteDetails(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = entityUidParam) String eventUid,
                                   @RequestParam(value = "back") String backMenu) throws URISyntaxException {
        // todo: decide whether to allow users to change the closing time (& permissions, of course)
        // todo: have some way of counting reminders and only allow once unless paid account
        // todo: reconsider whether to save URL here, might want to set back to null

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("details", eventUid) + "&back=" + backMenu  );
        Event vote = eventBroker.load(eventUid);
        boolean futureEvent = vote.getEventStartDateTime().isAfter(Instant.now());
        ResponseTotalsDTO voteResults = eventManager.getVoteResultsDTO(vote);

        USSDMenu menu;

        if (futureEvent) {
            EventLog userResponse = eventLogManager.getEventLogOfUser(vote, user, EventLogType.EventRSVP);

            List<String> otherResponses;
            if  (userResponse == null) {
                otherResponses = Arrays.asList("yes", "no", "abstain");
            } else {
                EventRSVPResponse response = EventRSVPResponse.fromString(userResponse.getMessage());
                switch (response) {
                    case YES:
                        otherResponses = Arrays.asList("no", "abstain");
                        break;
                    case NO:
                        otherResponses = Arrays.asList("yes", "abstain");
                        break;
                    case MAYBE:
                        otherResponses = Arrays.asList("yes", "no");
                        break;
                    default:
                        otherResponses = Arrays.asList("yes", "no", "abstain");
                        break;
                }
            }

            final String suffix = entityUidUrlSuffix + eventUid;
            final String responseText = (userResponse != null) ? userResponse.getMessage().toLowerCase() : "not voted yet";
            final String[] fields = new String[] { vote.getAncestorGroup().getName(""), vote.getName(),
                    "" + (voteResults.getNumberOfUsers() - voteResults.getNumberNoRSVP()), responseText };

            menu = new USSDMenu(getMessage(thisSection, "details", "future." + promptKey, fields, user));
            for (String voteOption : otherResponses) {
                menu.addMenuOption(voteMenus + "change-vote" + suffix + "&response=" + voteOption,
                                   getMessage(thisSection, "details", optionsKey + "change", voteOption, user));
            }
            // todo: enable once a bit clearer about use etc
            // menu.addMenuOption("reminder" + entityUidUrlSuffix + eventUid, getMessage(thisSection, "details", optionsKey + "reminder", user));
        } else {
            String[] fields = new String[]{vote.getAncestorGroup().getName(""), vote.getName(), "" + voteResults.getYes(),
                    "" + voteResults.getNo(), "" + voteResults.getMaybe(), "" + voteResults.getNumberNoRSVP()};
            menu = new USSDMenu(getMessage(thisSection, "details", promptKey, fields, user));
        }

        menu.addMenuOption(voteMenus + backMenu, getMessage(thisSection, "details", optionsKey + "back", user));

        return menuBuilder(menu);
    }

    /*@RequestMapping(value = path + "change-vote")
    @ResponseBody
    public Request changeVoteMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {

        final User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("change-vote", eventUid));
        final Event vote = eventBroker.load(eventUid);
        final String suffix = entityUidUrlSuffix + eventUid;

        USSDMenu menu;
        if (vote.getEventStartDateTime().isAfter(Instant.now())) {
            menu = new USSDMenu("Sorry! This vote is closed, you cannot change your vote");
            menu.addMenuOption(voteMenus + "details" + suffix, "Back to view vote");
            menu.addMenuOption(voteMenus + startMenu, "Back to votes menu");
            menu.addMenuOptions(optionsHomeExit(user));
        } else {
            final EventLog eventLog = eventLogManager.getEventLogOfUser(vote, user, EventLogType.EventRSVP);
            final String[] fields = { vote.getName(), eventLog.getMessage().toLowerCase() };
            menu = new USSDMenu("You voted X. What do you want to do?");
            menu.addMenuOption(voteMenus + "change-vote-do" + suffix, "Vote the other way");
            menu.addMenuOption(voteMenus + "details" + suffix, "Go back");
        }

        return menuBuilder(menu);
    }*/

    @RequestMapping(value = path + "change-vote")
    @ResponseBody
    public Request changeVoteDo(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = entityUidParam) String eventUid,
                                @RequestParam(value = "response") String response) throws  URISyntaxException {

        final User user = userManager.findByInputNumber(inputNumber, null);
        final Event vote = eventBroker.load(eventUid);

        USSDMenu menu;
        if (vote.getEventStartDateTime().isBefore(Instant.now())) {
            menu = new USSDMenu(getMessage(thisSection, "change", "error", user));
        } else {
            // todo: replace this hack once responses are handled better
            EventRSVPResponse voteResponse = (response.equals("abstain")) ? EventRSVPResponse.MAYBE :
                    EventRSVPResponse.fromString(response);
            eventLogManager.rsvpForEvent(vote, user, voteResponse);
            menu = new USSDMenu(getMessage(thisSection, "change", "done", response, user));
        }

        menu.addMenuOption(voteMenus + "details" + entityUidUrlSuffix + eventUid + "&back=open",
                           getMessage(thisSection, "change", optionsKey + "back", user));
        menu.addMenuOptions(optionsHomeExit(user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "reminder")
    @ResponseBody
    public Request sendVoteReminderConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {

        // todo: mention how many people will get the reminder
        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("reminder", eventUid));

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "reminder", promptKey, user));
        menu.addMenuOptions(optionsYesNo(user, voteMenus + "reminder-do?eventId=" + eventUid,
                voteMenus + "details?eventId=" + eventUid));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "reminder-do")
    @ResponseBody
    public Request sendVoteReminderDo(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {
        // use meeting reminder functions
        User user = userManager.findByInputNumber(inputNumber, null);
        eventBroker.sendManualReminder(user.getUid(), eventUid, "");
        return menuBuilder(new USSDMenu(getMessage(thisSection, "reminder-do", promptKey, user), optionsHomeExit(user)));
    }

    private String[] setCustomTime(String requestUid, String userInput, User user) {
        LocalDateTime parsedTime = eventUtil.parseDateTime(userInput);
        userLogger.recordUserInputtedDateTime(user.getUid(), userInput, "vote-custom", UserInterfaceType.USSD);
        eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, parsedTime);
        final String dateTimePrompt = "at " + parsedTime.format(dateTimeFormat);
        return new String[]{eventRequestBroker.load(requestUid).getName(), dateTimePrompt};
    }


    private String[] setStandardTime(String requestUid, String time, User user) {

        final ZonedDateTime proposedDateTime;
        final String dateTimePrompt = getMessage(thisSection, "confirm", "time." + time, user);

        ZonedDateTime zonedNow = Instant.now().atZone(DateTimeUtil.getSAST());

        switch (time) {
            case "instant":
                proposedDateTime = zonedNow.plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS);
                break;
            case "hour":
                proposedDateTime = zonedNow.plusHours(1L);
                break;
            case "day":
                proposedDateTime = zonedNow.plusDays(1L);
                break;
            case "week":
                proposedDateTime = zonedNow.plusWeeks(1L);
                break;
            default:
                // this should never be called, but need it else Java throws error -- defaulting to instant
                proposedDateTime = zonedNow.plusMinutes(7L);
                break;
        }

        eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, proposedDateTime.toLocalDateTime());
        EventRequest voteRequest = eventRequestBroker.load(requestUid);
        return new String[]{voteRequest.getName(), dateTimePrompt};

    }

    private String[] adjustSubject(String requestUid, String userInput, User user) {
        String dateTime;
        eventRequestBroker.updateName(user.getUid(), requestUid, userInput);
        EventRequest vote = eventRequestBroker.load(requestUid);
        if (vote.getEventStartDateTime().isBefore(Instant.now().plus(7, ChronoUnit.MINUTES))) {
            // user is manipulating an "instant" vote so need to reset the counter, else may expire before send
            // todo: make sure this is actually working with timezones
            eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, LocalDateTime.now().plusMinutes(7L));
            dateTime = getMessage(thisSection, "confirm", "time.instant", user);
        } else {
            // need a quick way to do "at" in i18n
            dateTime = "at " + vote.getEventDateTimeAtSAST().format(dateTimeFormat);
        }
        return new String[]{userInput, dateTime};
    }



}