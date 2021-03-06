package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.GroupLocation;

import java.time.LocalDate;

public interface GroupLocationRepository extends JpaRepository<GroupLocation, Long> {
	void deleteByGroupAndLocalDate(Group group, LocalDate localDate);
	GroupLocation findOneByGroupAndLocalDate(Group group, LocalDate localDate);
}
