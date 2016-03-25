package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "log_book_request")
public class LogBookRequest extends AbstractLogBookEntity {
	@Column(name = "replicate_to_subgroups", nullable = false)
	private boolean replicateToSubgroups;

	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "log_book_request_assigned_members",
			joinColumns = @JoinColumn(name = "log_book_request_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "user_id", nullable = false)
	)
	private Set<User> assignedMembers = new HashSet<>();

	private LogBookRequest() {
		// for JPA
	}

	public static LogBookRequest makeEmpty(User createdByUser, LogBookContainer parent) {
		LogBookRequest request = new LogBookRequest();
		request.uid = UIDGenerator.generateId();
		request.createdByUser = createdByUser;
		request.setParent(parent);

		return request;
	}

	public boolean isReplicateToSubgroups() {
		return replicateToSubgroups;
	}

	public void setReplicateToSubgroups(boolean replicateToSubgroups) {
		this.replicateToSubgroups = replicateToSubgroups;
	}

	public Set<User> getAssignedMembers() {
		if (assignedMembers == null) {
			assignedMembers = new HashSet<>();
		}
		return new HashSet<>(assignedMembers);
	}
}
