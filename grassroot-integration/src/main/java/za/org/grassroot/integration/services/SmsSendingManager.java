package za.org.grassroot.integration.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class SmsSendingManager implements SmsSendingService {

    // todo: add error and exception handling

    private Logger log = LoggerFactory.getLogger(SmsSendingManager.class);

    @Autowired
    private Environment environment;

    @Autowired
    private RestTemplate restTemplate;

    private String smsGatewayHost = "xml2sms.gsm.co.za";
    private String smsGatewayUsername = System.getenv("SMSUSER");
    private String smsGatewayPassword = System.getenv("SMSPASS");

    @Override
    public String sendSMS(String message, String destinationNumber) {

        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(smsGatewayHost);

        gatewayURI.path("send/").queryParam("username", smsGatewayUsername).queryParam("password", smsGatewayPassword);
        gatewayURI.queryParam("number", destinationNumber);
        gatewayURI.queryParam("message", message);

        // todo: test this on staging
        if (!environment.acceptsProfiles(GrassRootApplicationProfiles.INMEMORY)) {
            log.info("Sending SMS via URL: " + gatewayURI.toUriString());
            //@todo process response message
            String messageResult = restTemplate.getForObject(gatewayURI.build().toUri(), String.class);
            log.info("SMS...result..." + messageResult);
            return messageResult;
        }

        return null;
    }

}
