package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by luke on 2015/10/18.
 * note: For naming this entity, there could be confusion with a 'user account', but since we rarely use that terminology,
 * better that than 'institution', which seems like it would set us up for trouble (the term is loaded) down the road.
 */

@Entity
@Table(name="paid_account")
public class Account implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Timestamp createdDateTime;

    /*
    Doing this as one-to-many from account to users, rather than the inverse, because we are (much) more likely to have
    an account with 2-3 administrators than to have a user administering two accounts. The latter is not a non-zero
    possibility, but until/unless we have (very) strong user demand, catering to it is not worth many-to-many overheads
     */
    @OneToMany(mappedBy = "accountAdministered")
    private Set<User> administrators = new HashSet<>();

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<PaidGroup> paidGroups = new HashSet<>();

    @Basic
    @Column(name = "account_name")
    private String accountName;

    @Basic
    @Column(name = "primary_email")
    private String primaryEmail;

    /*
    Now a set of flags for which features the account has enabled (all will set to 'true' at first)
     */

    @Basic
    @Column
    private boolean enabled; // for future, in case we want to toggle a non-paying account on/off

    @Basic
    @Column(name="free_form")
    private boolean freeFormMessages;

    @Basic
    @Column(name="relayable")
    private boolean relayableMessages;

    @Basic
    @Column(name="logbook_extra")
    private boolean logbookExtraMessages;

    /*
    Constructors
     */

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
    }

    private Account() {
        // For JPA
    }

    public Account(String accountName, User administrator) {

        this.uid = UIDGenerator.generateId();
        this.accountName = accountName;

        this.administrators.add(administrator);

        this.enabled = true;
        this.freeFormMessages = true;
        this.relayableMessages = true;
        this.logbookExtraMessages = true;

    }

    public Account(String accountName, boolean enabled) {

        this.uid = UIDGenerator.generateId();
        this.accountName = accountName;
        this.enabled = enabled;
        this.freeFormMessages = enabled;
        this.relayableMessages = enabled;
        this.logbookExtraMessages = enabled;

    }

    public Account(String accountName, User administrator, String primaryEmail, boolean enabled) {

        this.uid = UIDGenerator.generateId();
        this.accountName = accountName;
        this.administrators = new HashSet<>();
        this.administrators.add(administrator);
        this.primaryEmail = primaryEmail;

        this.enabled = enabled;
        this.freeFormMessages = enabled;
        this.relayableMessages = enabled;

    }

    /*
    Getters and setters
     */

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) { this.createdDateTime = createdDateTime; }

    public Set<User> getAdministrators() {
        return administrators;
    }

    public void setAdministrators(Set<User> administrators) {
        this.administrators = administrators;
    }

    public Set<PaidGroup> getPaidGroups() {
        return paidGroups;
    }

    public void setPaidGroups(Set<PaidGroup> paidGroups) {
        this.paidGroups = paidGroups;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPrimaryEmail() {
        return primaryEmail;
    }

    public void setPrimaryEmail(String primaryEmail) {
        this.primaryEmail = primaryEmail;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFreeFormMessages() {
        return freeFormMessages;
    }

    public void setFreeFormMessages(boolean freeFormMessages) {
        this.freeFormMessages = freeFormMessages;
    }

    public boolean isRelayableMessages() {
        return relayableMessages;
    }

    public void setRelayableMessages(boolean relayableMessages) {
        this.relayableMessages = relayableMessages;
    }

    public boolean isLogbookExtraMessages() { return logbookExtraMessages; }

    public void setLogbookExtraMessages(boolean logbookExtraMessages) { this.logbookExtraMessages = logbookExtraMessages; }

    /*
    Helper methods for adding and removing administrators and groups
    todo: check for duplicates in all the adding methods, and that entity is in list for remove methods
     */

    public void addAdministrator(User administrator) {
        administrators.add(administrator);
    }

    public void removeAdministrator(User administrator) {
        administrators.remove(administrator);
    }

    public void addPaidGroup(PaidGroup paidGroup) {
        paidGroups.add(paidGroup);
    }

    public void removePaidGroup(PaidGroup paidGroup) {
        paidGroups.remove(paidGroup);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Account)) {
            return false;
        }

        Account account = (Account) o;

        if (getUid() != null ? !getUid().equals(account.getUid()) : account.getUid() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", accountName=" + accountName +
                ", primaryEmail=" + primaryEmail +
                ", enabled=" + enabled +
                ", free form messages='" + freeFormMessages + '\'' +
                ", relayable messages='" + relayableMessages + '\'' +
                ", number administrators='" + administrators.size() + '\'' +
                ", number groups paid for='" + paidGroups.size() + '\'' +
                '}';
    }

}
