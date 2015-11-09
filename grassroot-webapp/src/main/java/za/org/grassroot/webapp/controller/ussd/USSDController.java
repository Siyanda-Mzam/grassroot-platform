package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by luke on 2015/08/14.
 * todo: Expand -- a lot -- the various methods needed to handle phone number inputs
 */
public class USSDController {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

    @Autowired
    EventManagementService eventManager;

    @Autowired
    @Qualifier("messageSource")
    MessageSource messageSource;

    /**
     * SECTION: Constants used throughout the code
     */

    // adopting a convention to capitalize constant strings that are used across all controllers
    // todo: more elegant way of handling "." and "/" difference btw URL mapping and message (but note @RequestMapping can't take a method)

    // Constants used in URL mapping and message handling
    protected static final String USSD_BASE = "/ussd/", MTG_MENUS = "mtg/", USER_MENUS = "user/", GROUP_MENUS = "group/";
    protected static final String VOTE_MENUS = "vote/", LOG_MENUS = "log", U404="error"; // leaving off '/' for now, until built
    protected static final String PHONE_PARAM = "msisdn", TEXT_PARAM = "request", GROUP_PARAM = "groupId", EVENT_PARAM = "eventId", TOKEN_PARAM="token";
    protected static final String START_KEY = "start", PASSED_FIELD = "menukey", YESNO_FIELD = "confirmed",
            GROUPID_URL = ("?" + GROUP_PARAM + "="), EVENTID_URL = ("?" + EVENT_PARAM + "="), TOKEN_URL = ("&" + TOKEN_PARAM + "="), DO_SUFFIX = "-do";

    // Constants used in i18n and message handling
    protected static final String HOME_KEY = "home", MTG_KEY = "mtg", USER_KEY = "user", GROUP_KEY = "group", VOTE_KEY = "vote", LOG_KEY = "log";
    protected static final String PROMPT = "prompt", PROMPT_ERROR = "prompt.error", OPTION = "options.", VALID="valid", ERROR="error";

    // Constants used in pagination of groups
    protected static final Integer PAGE_LENGTH = 3;

    protected final String smsHost = "xml2sms.gsm.co.za";
    protected final String smsUsername = System.getenv("SMSUSER");
    protected final String smsPassword = System.getenv("SMSPASS");

    // protected final String baseURI = "http://meeting-organizer.herokuapp.com/ussd/";
    protected final String baseURI = System.getenv("APP_URL") + USSD_BASE;


    /**
     * SECTION: Menu building methods
     */

    protected List<Option> createMenu(Map<String, String> menuOptions) throws URISyntaxException {
        List<Option> menuToBuild = new ArrayList<>();
        Integer counter = 1;
        for (Map.Entry<String, String> option : menuOptions.entrySet()) {
            menuToBuild.add(new Option(option.getValue(), counter, counter, new URI(baseURI + option.getKey()), true));
            counter++;
        }
        return menuToBuild;
    }

    /*
    integrating check for menu length in here, to avoid writing it in every return
    defaulting to not first screen, can do an override in start (shouldn't cause speed issues, but watch)
    not bothering to check length on a free text menu, since the odds of those exceeding are very low (and then a UX issue...)
    */
    protected Request menuBuilder(USSDMenu thisMenu) throws URISyntaxException {
        Request menuRequest;
        if (thisMenu.isFreeText()) {
            menuRequest = new Request(thisMenu.getPromptMessage(), freeText(thisMenu.getNextURI()));
        } else if (checkMenuLength(thisMenu, false)) {
            menuRequest = new Request(thisMenu.getPromptMessage(), createMenu(thisMenu.getMenuOptions()));
        } else {
            // note: this runs the risk of cutting off crucial end-of-prompt info, but is 'least bad' option so far (other is just an error message)
            Integer charsToTrim = thisMenu.getMenuCharLength() - 159; // adding a character, for safety
            String currentPrompt = thisMenu.getPromptMessage();
            String revisedPrompt = currentPrompt.substring(0, currentPrompt.length() - charsToTrim);
            menuRequest = new Request(revisedPrompt, createMenu(thisMenu.getMenuOptions()));
        }
        return menuRequest;
    }

    protected List<Option> freeText(String urlEnding) throws URISyntaxException {
        return Collections.singletonList(new Option("", 1, 1, new URI(baseURI + urlEnding), false));
    }

    /*
    method to check the length of a USSD menu and make sure it is under 140/160 chars.
    at present, am just returning a boolean, but may want to truncate / throw an exception or do something else
    last: writing it here, so that if we change aggregator / view technology, we adjust here
     */
    protected boolean checkMenuLength(USSDMenu menuToCheck, boolean firstMenu) {

        Integer enumLength = ("1. ").length();
        Integer characterLimit = firstMenu ? 140 : 160;

        log.info("Length of menu: " + menuToCheck.getMenuCharLength(enumLength));

        return (menuToCheck.getMenuCharLength(enumLength) < characterLimit); // might be able to get away with <=, but prefer to be conservative
    }

    /**
     * SECTION: Auxiliary methods used in creating, sorting, displaying groups in USSD menus / from USSD input
     * todo: extend pagination, to allow for multiple pages, and/or better sorting (last accessed & most accessed)
     */

    protected USSDMenu userGroupMenu(User user, String menuPrompt, String urlForExistingGroups, String urlForNewGroup, String groupParam, Integer pageNumber)
            throws URISyntaxException {

        USSDMenu menu = new USSDMenu(menuPrompt);
        Page<Group> groupsPartOf = groupManager.getPageOfGroups(user, pageNumber, PAGE_LENGTH);

        final String formedUrl = (!urlForExistingGroups.contains("?")) ?
                (urlForExistingGroups + GROUPID_URL) :
                (urlForExistingGroups + "&" + groupParam + "=");

        String finalOption = (urlForNewGroup != null) ? getMessage(GROUP_KEY, "create", "option", user) : "";
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM");

        for (Group groupToList : groupsPartOf) {
            String groupName = (groupToList.hasName()) ? groupToList.getGroupName() :
                    getMessage(GROUP_KEY, "unnamed", "label", sdf.format(groupToList.getCreatedDateTime()), user);
            menu.addMenuOption(formedUrl + groupToList.getId(), groupName);
        }

        if (groupsPartOf.hasNext())
            menu.addMenuOption(assemblePaginatedURI(menuPrompt, urlForExistingGroups, urlForNewGroup, pageNumber + 1), "More groups");

        if (groupsPartOf.hasPrevious())
            menu.addMenuOption(assemblePaginatedURI(menuPrompt, urlForExistingGroups, urlForNewGroup, pageNumber -1), "Back");

        if (urlForNewGroup != null)
            menu.addMenuOption(urlForNewGroup, finalOption);

        return menu;
    }

    private String assemblePaginatedURI(String menuPrompt, String existingGroupUri, String newGroupUri, Integer pageNumber) {
        String newGroupParameter = (newGroupUri != null) ? "&newUri=" + encodeParamater(newGroupUri) : "";
        return "group_page?prompt=" + encodeParamater(menuPrompt) + "&existingUri=" + encodeParamater(existingGroupUri)
                + newGroupParameter + "&page=" + pageNumber;
    }

    // slightly simplified version, with an option to leave off the new group
    protected USSDMenu userGroupMenu(User sessionUser, String promptMessage, String path, boolean optionNewGroup)
            throws URISyntaxException {

        final String formedUrl = (!path.contains("?")) ? (path + GROUPID_URL) : (path + "&" + GROUP_PARAM + "=");
        final String newGroupUrl = (optionNewGroup) ? formedUrl + "0" : null;

        return userGroupMenu(sessionUser, promptMessage, path, newGroupUrl, GROUP_PARAM);
    }

    protected USSDMenu userGroupMenu(User sessionUser, String promptMessage, String existingPath, String newUri, String groupParam)
            throws URISyntaxException {
        return userGroupMenu(sessionUser, promptMessage, existingPath, newUri, groupParam, 0);
    }

    /*
     Methods to enter a loop of entering a group, handling input, and exiting again--will be used in several controllers
     note: by luke -- I've moved processing the string into separate phone numbers here, because it's actually a problem
     only for the USSD module. on the web application, and/or the android app, we have a form with validation logic,
     and separate text boxes for each of the numbers, so we get a tidy list of phone number strings
      */

    protected USSDMenu processGroupInput(Long groupId, String userInput, String sectionKey, String menuKey,
                                         String promptKey, String returnUri, User sessionUser) {

        USSDMenu thisMenu = new USSDMenu("");
        thisMenu.setFreeText(true);

        Map<String, List<String>> enteredNumbers = za.org.grassroot.core.util.PhoneNumberUtil.splitPhoneNumbers(userInput);

        List<String> errorNumbers = enteredNumbers.get("error");

        if (errorNumbers.size() == 0) {
            thisMenu.setPromptMessage(getMessage(sectionKey, menuKey + DO_SUFFIX, PROMPT + "." + promptKey, sessionUser));
        } else {
            // assemble the error menu
            String listErrors = String.join(", ", errorNumbers);
            String promptMessage = getMessage(sectionKey, menuKey + DO_SUFFIX, PROMPT_ERROR, listErrors, sessionUser);
            thisMenu.setPromptMessage(promptMessage);
        }

        thisMenu.setNextURI(returnUri  + GROUPID_URL + groupId); // loop back to group menu
        return thisMenu;
    }


    /**
     * SECTION: i18n helper methods and encoding , as well some default menus used often
     * todo: replace these error messages, especially the 'no user' error
     */

    Request tooLongError = new Request("Error! Menu is too long.", new ArrayList<Option>());
    Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<Option>());
    Request noGroupError = new Request("Sorry! Something went wrong finding the group.", new ArrayList<Option>());
    Request exitScreen = new Request("Thanks! We're done.", new ArrayList<Option>());

    protected Map<String, String> optionsHomeExit(User sessionUser) {
        return ImmutableMap.<String, String>builder().
                put("start", getMessage(START_KEY, sessionUser)).
                put("exit", getMessage("exit.option", sessionUser)).build();
    }

    protected Map<String, String> optionsYesNo(User sessionUser, String yesUri, String noUri) {
        return ImmutableMap.<String, String>builder().
                put(yesUri + "&" + YESNO_FIELD + "=yes", getMessage(OPTION + "yes", sessionUser)).
                put(noUri + "&" + YESNO_FIELD + "=no", getMessage(OPTION + "no", sessionUser)).build();
    }

    protected Map<String, String> optionsYesNo(User sesionUser, String nextUri) {
        return optionsYesNo(sesionUser, nextUri, nextUri);
    }

    protected String encodeParamater(String stringToEncode) {

        String encodedString;
        try { encodedString = URLEncoder.encode(stringToEncode, "UTF-8"); } // need to do this in case we get interrupted, to deal with spaces
        catch (UnsupportedEncodingException e) { encodedString = stringToEncode; } // todo: handle errors better

        return encodedString;
    }

    protected String getMessage(String section, String menuKey, String messageLocation, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    // convenience function for when passing just a name (of user or group, for example)
    protected String getMessage(String section, String menuKey, String messageLocation, String parameter, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, new String[]{ parameter }, new Locale(getLanguage(sessionUser)));
    }

    protected String getMessage(String section, String menuKey, String messageLocation, String[] parameters, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, parameters, new Locale(getLanguage(sessionUser)));
    }

    // for convenience, sometimes easier to read this way than passing around user instance
    protected String getMessage(String section, String menuKey, String messageLocation, Locale sessionLocale) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, sessionLocale);
    }

    // final convenience version, for the root strings, stripping out "."
    protected String getMessage(String messageKey, User sessionUser) {
        return messageSource.getMessage("ussd." + messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    // todo move this somewhere else, and/or clean up nullability in User class, but if put it there, confuses Hibernate (wants a setter)
    protected String getLanguage(User user) {
        // todo some validation on the locale code, above just checking it's not null
        return (user.getLanguageCode() == null) ? Locale.US.getLanguage(): user.getLanguageCode();
    }

}
