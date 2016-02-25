package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupLogType;

import javax.transaction.Transactional;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Luke Jordan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GroupLogRepositoryTest {

    private Logger log = Logger.getLogger(getClass().getName());

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupLogRepository groupLogRepository;


    @Test
    public void shouldSaveAndRetrieveGroupLog() throws Exception {

        assertThat(groupLogRepository.count(), is(0L));

        User userToDoTests = new User("0810001111");
        userToDoTests = userRepository.save(userToDoTests);

        Group groupToCreate = new Group("testGroup", userToDoTests);
        groupToCreate = groupRepository.save(groupToCreate);

        GroupLog groupLog = new GroupLog(groupToCreate.getId(), userToDoTests.getId(), GroupLogType.GROUP_ADDED, 0L);
        assertNull(groupLog.getId());
        assertNull(groupLog.getCreatedDateTime());
        groupLogRepository.save(groupLog);

        assertThat(groupLogRepository.count(), is(1l));
        GroupLog groupLogFromDb = groupLogRepository.findByGroupId(groupToCreate.getId()).iterator().next();
        assertNotNull(groupLogFromDb.getId());
        assertNotNull(groupLogFromDb.getCreatedDateTime());
        assertThat(groupLogFromDb.getGroupId(), is(groupToCreate.getId()));
        assertThat(groupLogFromDb.getUserId(), is(userToDoTests.getId()));
    }

    @Test
    public void shouldReturnOnlyMostRecentLog() throws Exception {

        assertThat(groupLogRepository.count(), is(0L));

        User userToDoTests = userRepository.save(new User("0810002222"));
        Group groupToCreate = groupRepository.save(new Group("testGroup", userToDoTests));

        GroupLog groupLog1 = new GroupLog(groupToCreate.getId(), userToDoTests.getId(), GroupLogType.GROUP_ADDED, 0L);
        assertNull(groupLog1.getId());
        assertNull(groupLog1.getCreatedDateTime());
        groupLogRepository.save(groupLog1);

        GroupLog groupLog2 = new GroupLog(groupToCreate.getId(), userToDoTests.getId(), GroupLogType.GROUP_MEMBER_ADDED, 0L);
        assertNull(groupLog2.getId());
        assertNull(groupLog2.getCreatedDateTime());
        groupLogRepository.save(groupLog2);

        assertThat(groupLogRepository.count(), is(2l));
        GroupLog groupLogFromDb = groupLogRepository.findFirstByGroupIdOrderByCreatedDateTimeDesc(groupToCreate.getId());
        assertNotNull(groupLogFromDb.getId());
        assertNotNull(groupLogFromDb.getCreatedDateTime());
        assertThat(groupLogFromDb.getGroupId(), is(groupToCreate.getId()));
        assertThat(groupLogFromDb.getUserId(), is(userToDoTests.getId()));

    }



}


