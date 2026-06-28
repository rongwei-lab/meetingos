package com.meetingos.config;

import com.meetingos.security.AppProperties;
import com.meetingos.security.InitialAdminProperties;
import com.meetingos.security.PublicQueryProperties;
import com.meetingos.security.SecurityProperties;
import com.meetingos.security.TduckProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SecurityProperties.class,
        TduckProperties.class,
        PublicQueryProperties.class,
        AppProperties.class,
        InitialAdminProperties.class
})
public class PropertiesConfig {
}
