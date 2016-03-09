package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.services.MembershipInfo;

import java.util.*;

/**
 * Created by luke on 2015/09/13.
 * Wrapper class to get the group creation forms to work (hopefully) a lot better, since Thymeleaf / Spring MVC require
 * everything to be in the same class in order to work okay.
 */

public class GroupWrapper {

    private Group group;
    private String groupName;

    private boolean hasParent;
    private Group parentGroup;
    private Long parentId;

    private String parentName;

    private boolean discoverable;
    private boolean generateToken;
    private Integer tokenDaysValid;


    // need to use a list so that we can add and remove
    private List<MembershipInfo> listOfMembers = new ArrayList<>();

    // leaving out setters for group and parent as those are set at construction

    public GroupWrapper() {
        this.group = Group.makeEmpty();
        this.generateToken = false;
        this.discoverable = false;
        // this.template = GroupPermissionTemplate.DEFAULT_GROUP;
    }

    public GroupWrapper(Group parentGroup) {
        this();

        this.hasParent = true;
        this.parentGroup = Objects.requireNonNull(parentGroup);
        this.parentId = parentGroup.getId();
        this.parentName = parentGroup.getGroupName();
        this.listOfMembers.addAll(MembershipInfo.createFromMembers(parentGroup.getMemberships()));
        this.discoverable = parentGroup.isDiscoverable();
        // this.template = GroupPermissionTemplate.DEFAULT_GROUP; // todo: figure out if/how to store / inherit this
    }

    public Group getGroup() { return group; }

    public Group getParent() { return parentGroup; }

    public String getGroupName() { return groupName; }

    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean getHasParent() { return hasParent; }

    public void setHasParent(boolean hasParent) { this.hasParent = hasParent; }

    public Long getParentId() { return parentId; }

    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public boolean getGenerateToken() { return generateToken; }

    public void setGenerateToken(boolean generateToken) { this.generateToken = generateToken; }

    public Integer getTokenDaysValid() { return tokenDaysValid; }

    public void setTokenDaysValid(Integer tokenDaysValid) { this.tokenDaysValid = tokenDaysValid; }

    public Set<MembershipInfo> getAddedMembers() { return new HashSet<>(listOfMembers); }

    public List<MembershipInfo> getListOfMembers() { return listOfMembers; }

    public void setListOfMembers(List<MembershipInfo> listOfMembers) { this.listOfMembers = new ArrayList<>(listOfMembers); }

    public void setAddedMembers(Set<MembershipInfo> addedMembers) {
        this.listOfMembers = new ArrayList<>(addedMembers);
    }

    public void addMember(MembershipInfo newMember) {
        this.listOfMembers.add(newMember);
    }

    /*
    Helper method for quickly populating one, for the group modification
     */

    public void populate(Group groupToModify) {

        // no need to do anything about the token -- handled separately

        this.group = groupToModify;
        this.groupName = group.getGroupName();
        this.parentGroup = group.getParent();
        this.discoverable = group.isDiscoverable();

        if (parentGroup != null) {
            this.hasParent = true;
            this.parentId = parentGroup.getId();
            this.parentName = parentGroup.getGroupName();
        }

        this.listOfMembers.addAll(MembershipInfo.createFromMembers(groupToModify.getMemberships()));

    }

}
